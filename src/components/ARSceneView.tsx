import React, { useRef, useEffect, useState, useCallback } from 'react';
import {
	View,
	Text,
	TouchableOpacity,
	Platform,
	PermissionsAndroid,
	AppState,
	findNodeHandle,
} from 'react-native';
import { NativeModules } from 'react-native';
import { arSceneView } from '../styles/componentStyles';

const { SceneViewModule, OnnxRuntimeModule } = NativeModules;

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

const ARSceneView: React.FC<ARSceneViewProps> = ({
	style,
	onClose,
	onError,
	onSceneReady,
}) => {
	const viewRef = useRef<View>(null);
	const [isInitialized, setIsInitialized] = useState(false);
	const [detections, setDetections] = useState<BoundingBox[]>([]);
	const [isProcessing, setIsProcessing] = useState(false);
	const appState = useRef(AppState.currentState);

	const requestCameraPermission = useCallback(async () => {
		if (Platform.OS !== 'android') return true;
		const granted = await PermissionsAndroid.request(
			PermissionsAndroid.PERMISSIONS.CAMERA,
		);
		return granted === PermissionsAndroid.RESULTS.GRANTED;
	}, []);

	const initializeScene = useCallback(async () => {
		try {
			const granted = await requestCameraPermission();
			if (!granted) throw new Error('Camera permission denied');

			const reactTag = findNodeHandle(viewRef.current);
			if (!reactTag) throw new Error('View reference not found');

			// เรียก Native Module ให้ตรงกับ SceneViewModule.kt
			await new Promise((resolve, reject) => {
				SceneViewModule.initializeScene(reactTag, {
					resolve: resolve,
					reject: (err: string) => reject(new Error(err)),
				});
			});

			await new Promise((resolve, reject) => {
				SceneViewModule.startARSession({
					resolve: resolve,
					reject: (err: string) => reject(new Error(err)),
				});
			});

			setIsInitialized(true);
			onSceneReady?.();
		} catch (err) {
			onError(
				err instanceof Error ? err.message : 'Failed to initialize AR scene',
			);
		}
	}, [onError, onSceneReady, requestCameraPermission]);

	const processFrame = useCallback(async () => {
		if (!isInitialized || isProcessing) return;
		setIsProcessing(true);

		try {
			const image: any = await new Promise((resolve, reject) => {
				SceneViewModule.captureFrame({
					resolve: resolve,
					reject: (err: string) => reject(new Error(err)),
				});
			});

			if (!image) return;

			// inference
			const detected: BoundingBox[] = await new Promise((resolve, reject) => {
				OnnxRuntimeModule.runInferenceFromFrame(image, {
					resolve: resolve,
					reject: (err: string) => reject(new Error(err)),
				});
			});

			setDetections(detected || []);

			// render AR boxes
			for (const det of detected || []) {
				if (det.confidence > 0.7) {
					const centerX = det.x + det.width / 2;
					const centerY = det.y + det.height / 2;

					// Native module ต้อง implement hitTestWithOffset หรือใช้ค่า default
					const pose: Pose6DoF = await new Promise((resolve, reject) => {
						SceneViewModule.hitTestWithOffset(centerX, centerY, 0.05, {
							resolve: resolve,
							reject: (err: string) => reject(new Error(err)),
						});
					});

					await new Promise((resolve, reject) => {
						SceneViewModule.renderBlueBox(pose, {
							resolve: resolve,
							reject: (err: string) => reject(new Error(err)),
						});
					});
				}
			}
		} catch (err) {
			console.error('Frame processing error:', err);
		} finally {
			setIsProcessing(false);
		}
	}, [isInitialized, isProcessing]);

	useEffect(() => {
		const timer = setTimeout(initializeScene, 100);
		return () => clearTimeout(timer);
	}, [initializeScene]);

	// frame loop
	useEffect(() => {
		if (!isInitialized) return;
		const intervalId = setInterval(processFrame, 100); // 10fps
		return () => clearInterval(intervalId);
	}, [isInitialized, processFrame]);

	const handleAppStateChange = useCallback(
		nextAppState => {
			if (
				appState.current.match(/inactive|background/) &&
				nextAppState === 'active'
			) {
				if (isInitialized) SceneViewModule.resumeScene?.().catch(console.error);
			} else if (
				appState.current === 'active' &&
				nextAppState.match(/inactive|background/)
			) {
				if (isInitialized) SceneViewModule.pauseScene?.().catch(console.error);
			}
			appState.current = nextAppState;
		},
		[isInitialized],
	);

	useEffect(() => {
		const subscription = AppState.addEventListener(
			'change',
			handleAppStateChange,
		);
		return () => subscription.remove();
	}, [handleAppStateChange]);

	useEffect(() => {
		return () => {
			if (isInitialized) SceneViewModule.cleanup?.().catch(console.error);
		};
	}, [isInitialized]);

	const renderDetectionOverlays = () =>
		detections.map((d, i) => (
			<View
				key={i}
				style={[
					arSceneView.boundingBox,
					{ left: d.x, top: d.y, width: d.width, height: d.height },
				]}
			>
				<Text style={arSceneView.confidenceText}>
					{(d.confidence * 100).toFixed(1)}%
				</Text>
			</View>
		));

	return (
		<View style={[arSceneView.container, style]}>
			<View ref={viewRef} style={arSceneView.sceneView} collapsable={false} />
			<View style={arSceneView.overlayContainer}>
				{renderDetectionOverlays()}
			</View>
			<View style={arSceneView.controlPanel}>
				<TouchableOpacity style={arSceneView.closeButton} onPress={onClose}>
					<Text style={arSceneView.closeButtonText}>Close AR</Text>
				</TouchableOpacity>
				<View style={arSceneView.statusContainer}>
					<Text style={arSceneView.statusText}>
						{isInitialized ? '✅ AR Active' : '⏳ Initializing...'}
					</Text>
					<Text style={arSceneView.statusText}>
						Detections: {detections.length}
					</Text>
				</View>
			</View>
		</View>
	);
};

export default ARSceneView;
