import React, { useRef, useEffect, useState, useCallback } from 'react';
import {
	View,
	Text,
	TouchableOpacity,
	Platform,
	PermissionsAndroid,
	AppState,
	findNodeHandle,
	NativeModules,
	InteractionManager,
	requireNativeComponent,
	Alert,
} from 'react-native';
import { arSceneView } from '../styles/componentStyles';

const { SceneViewModule } = NativeModules;

interface NativeARSceneViewProps {
	ref?: React.Ref<any>;
	style?: any;
	onLayout?: () => void;
	collapsable?: boolean;
}

const NativeARSceneView =
	requireNativeComponent<NativeARSceneViewProps>('ARSceneView');

interface ARSceneViewProps {
	style?: any;
	onClose: () => void;
	onError: (error: string) => void;
	onSceneReady?: () => void;
}

const ARSceneView: React.FC<ARSceneViewProps> = ({
	style,
	onClose,
	onError,
	onSceneReady,
}) => {
	const viewRef = useRef(null);
	const [isInitialized, setIsInitialized] = useState(false);
	const [isMounted, setIsMounted] = useState(false);
	const appState = useRef(AppState.currentState);

	const handleViewLayout = useCallback(() => {
		if (!isMounted) {
			console.log('📱 View mounted');
			setIsMounted(true);
		}
	}, [isMounted]);

	const requestCameraPermission = useCallback(async () => {
		if (Platform.OS !== 'android') return true;
		try {
			const granted = await PermissionsAndroid.request(
				PermissionsAndroid.PERMISSIONS.CAMERA,
				{
					title: 'Camera Permission',
					message: 'This app needs access to your camera for AR functionality.',
					buttonNeutral: 'Ask Me Later',
					buttonNegative: 'Cancel',
					buttonPositive: 'OK',
				},
			);
			return granted === PermissionsAndroid.RESULTS.GRANTED;
		} catch (err) {
			console.warn('❌ Permission error:', err);
			return false;
		}
	}, []);

	const initializeScene = useCallback(async () => {
		if (!isMounted) return;

		try {
			const granted = await requestCameraPermission();
			if (!granted) {
				throw new Error('Camera permission denied');
			}

			const reactTag = findNodeHandle(viewRef.current);
			if (!reactTag) throw new Error('View reference not found');

			if (!SceneViewModule || typeof SceneViewModule.initializeScene !== 'function') {
				throw new Error('SceneViewModule not available');
			}

			await SceneViewModule.initializeScene(reactTag);
			await SceneViewModule.startARSession();
			setIsInitialized(true);

			onSceneReady?.();
		} catch (err) {
			console.error('❌ AR Initialization error:', err);
			onError(err instanceof Error ? err.message : 'Failed to initialize AR scene');
			Alert.alert('AR Initialization Failed', err.message || 'Unknown error', [
				{ text: 'OK' },
			]);
		}
	}, [isMounted, onError, onSceneReady, requestCameraPermission]);

	const renderBlueBox = useCallback(async () => {
		if (!isInitialized) return;

		try {
			const pose = {
				position: [0.0, 0.0, -0.5], // อยู่หน้ากล้อง 0.5 เมตร
				rotation: [0.0, 0.0, 0.0],
			};
			await SceneViewModule.renderBlueBox(pose);
		} catch (err) {
			console.error('❌ Failed to render blue box:', err);
		}
	}, [isInitialized]);

	useEffect(() => {
		if (!isMounted) return;
		const timer = setTimeout(initializeScene, 300);
		return () => clearTimeout(timer);
	}, [isMounted, initializeScene]);

	useEffect(() => {
		if (isInitialized) {
			renderBlueBox(); // render Blue Box เมื่อ AR ready
		}
	}, [isInitialized, renderBlueBox]);

	useEffect(() => {
		return () => {
			console.log('🧹 Cleaning up AR Scene...');
			if (isInitialized && SceneViewModule && SceneViewModule.cleanup) {
				SceneViewModule.cleanup().catch(console.error);
			}
		};
	}, [isInitialized]);

	return (
		<View style={[arSceneView.container, style]}>
			<NativeARSceneView
				ref={viewRef}
				style={arSceneView.sceneView}
				onLayout={handleViewLayout}
				collapsable={false}
			/>

			<View style={arSceneView.controlsContainer}>
				<TouchableOpacity style={arSceneView.closeButton} onPress={onClose}>
					<Text style={arSceneView.closeButtonText}>✕ Close</Text>
				</TouchableOpacity>
			</View>
		</View>
	);
};

export default ARSceneView;
