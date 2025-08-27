import React, { useRef, useEffect, useState, useCallback } from 'react';
import {
	View,
	Text,
	TouchableOpacity,
	Platform,
	PermissionsAndroid,
	AppState,
	AppStateStatus,
	findNodeHandle,
} from 'react-native';
import { NativeModules } from 'react-native';
import { arSceneView } from '../styles/componentStyles';

const { SceneViewModule } = NativeModules;

interface BoundingBox {
	x: number;
	y: number;
	width: number;
	height: number;
	confidence: number;
	class: number;
}

interface Pose6DoF {
	position: [number, number, number];
	rotation: [number, number, number];
}

interface ARSceneViewProps {
	style?: any;
	onClose: () => void;
	onError: (error: string) => void;
	onSceneReady?: () => void;
}

const ARSceneViewMinimal: React.FC<ARSceneViewProps> = ({
	style,
	onClose,
	onError,
	onSceneReady,
}) => {
	const viewRef = useRef<View>(null);
	const [isInitialized, setIsInitialized] = useState(false);
	const appState = useRef(AppState.currentState);

	// ขอ permission
	const requestCameraPermission = useCallback(async () => {
		if (Platform.OS !== 'android') return true;

		try {
			const granted = await PermissionsAndroid.request(
				PermissionsAndroid.PERMISSIONS.CAMERA,
				{
					title: 'Camera Permission',
					message: 'This app needs camera access for AR functionality',
					buttonNeutral: 'Ask Me Later',
					buttonNegative: 'Cancel',
					buttonPositive: 'OK',
				},
			);
			return granted === PermissionsAndroid.RESULTS.GRANTED;
		} catch (err) {
			console.error('Permission error:', err);
			return false;
		}
	}, []);

	// init scene
	const initializeScene = useCallback(async () => {
		try {
			const granted = await requestCameraPermission();
			if (!granted) throw new Error('Camera permission denied');

			const reactTag = findNodeHandle(viewRef.current);
			if (!reactTag) throw new Error('Could not find view reference');

			await SceneViewModule.initializeScene(reactTag);
			await SceneViewModule.startARSession();

			setIsInitialized(true);
			onSceneReady?.();
		} catch (error) {
			console.error('Failed to initialize AR scene:', error);
			onError(
				error instanceof Error
					? error.message
					: 'Failed to initialize AR scene',
			);
		}
	}, [onError, onSceneReady, requestCameraPermission]);

	// lifecycle: appstate
	const handleAppStateChange = useCallback(
		(nextAppState: AppStateStatus) => {
			if (
				appState.current.match(/inactive|background/) &&
				nextAppState === 'active'
			) {
				if (isInitialized) {
					SceneViewModule.resumeScene?.().catch(console.error);
				}
			} else if (
				appState.current === 'active' &&
				nextAppState.match(/inactive|background/)
			) {
				if (isInitialized) {
					SceneViewModule.pauseScene?.().catch(console.error);
				}
			}
			appState.current = nextAppState;
		},
		[isInitialized],
	);

	// mount/unmount
	useEffect(() => {
		const timer = setTimeout(initializeScene, 100);
		return () => clearTimeout(timer);
	}, [initializeScene]);

	useEffect(() => {
		const subscription = AppState.addEventListener(
			'change',
			handleAppStateChange,
		);
		return () => subscription.remove();
	}, [handleAppStateChange]);

	useEffect(() => {
		return () => {
			if (isInitialized) {
				SceneViewModule.cleanup?.().catch(console.error);
			}
		};
	}, [isInitialized]);

	return (
		<View style={[arSceneView.container, style]}>
			<View ref={viewRef} style={arSceneView.sceneView} collapsable={false} />
			<View style={arSceneView.controlPanel}>
				<TouchableOpacity style={arSceneView.closeButton} onPress={onClose}>
					<Text style={arSceneView.closeButtonText}>Close AR</Text>
				</TouchableOpacity>
				<View style={arSceneView.statusContainer}>
					<Text style={arSceneView.statusText}>
						{isInitialized ? '✅ AR Active' : '⏳ Initializing...'}
					</Text>
				</View>
			</View>
		</View>
	);
};

export default ARSceneViewMinimal;
