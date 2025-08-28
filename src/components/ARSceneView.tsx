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
} from 'react-native';
import { arSceneView } from '../styles/componentStyles';

const { SceneViewModule, OnnxRuntimeModule } = NativeModules;

// เชื่อมกับ ARSceneViewManager.kt
const NativeARSceneView = requireNativeComponent('ARSceneView');

interface BoundingBox {
	x: number;
	y: number;
	width: number;
	height: number;
	confidence: number;
	class: number;
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
	const viewRef = useRef(null);
	const [isInitialized, setIsInitialized] = useState(false);
	const [isMounted, setIsMounted] = useState(false);
	const [detections, setDetections] = useState<BoundingBox[]>([]);
	const [isProcessing, setIsProcessing] = useState(false);
	const appState = useRef(AppState.currentState);

	// Debug: ตรวจสอบ modules
	useEffect(() => {
		console.log('Available NativeModules:', Object.keys(NativeModules));
		console.log('SceneViewModule:', SceneViewModule);
	}, []);

	const handleViewLayout = useCallback(() => {
		if (!isMounted) {
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
			console.warn('Permission error:', err);
			return false;
		}
	}, []);

	const initializeScene = useCallback(async () => {
		if (!isMounted) return;
		try {
			const granted = await requestCameraPermission();
			if (!granted) throw new Error('Camera permission denied');

			await new Promise<void>(resolve =>
				InteractionManager.runAfterInteractions(() => resolve()),
			);
			await new Promise<void>(resolve => setTimeout(resolve, 200));

			const reactTag = findNodeHandle(viewRef.current);
			console.log('View handle:', reactTag);

			if (!reactTag) throw new Error('View reference not found');
			if (!SceneViewModule) throw new Error('SceneViewModule is not available');
			if (typeof SceneViewModule.initializeScene !== 'function')
				throw new Error('SceneViewModule.initializeScene is not a function');

			// ✅ เปิดกล้องก่อน
			await SceneViewModule.initializeScene(reactTag);
			await SceneViewModule.startARSession();

			// ✅ พยายาม init ONNX แต่ถ้าไม่ได้ ก็ไม่เป็นไร
			if (
				OnnxRuntimeModule &&
				typeof OnnxRuntimeModule.initializeModel === 'function'
			) {
				try {
					await OnnxRuntimeModule.initializeModel();
					console.log('ONNX model initialized ✅');
				} catch (onnxErr) {
					console.warn('ONNX init failed, but camera is running:', onnxErr);
				}
			} else {
				console.warn(
					'OnnxRuntimeModule not available, running AR camera only.',
				);
			}

			setIsInitialized(true);
			onSceneReady?.();
		} catch (err) {
			console.error('AR Initialization error:', err);
			onError(
				err instanceof Error ? err.message : 'Failed to initialize AR scene',
			);
		}
	}, [isMounted, onError, onSceneReady, requestCameraPermission]);

	const processFrame = useCallback(async () => {
		if (!isInitialized || isProcessing) return;
		if (
			!OnnxRuntimeModule ||
			typeof OnnxRuntimeModule.runInferenceFromFrame !== 'function'
		) {
			// ❌ ถ้าไม่มี onnx ก็ไม่ต้องทำ inference
			return;
		}

		setIsProcessing(true);
		try {
			const frameData = await SceneViewModule.captureFrame();
			if (!frameData) return;

			const rawDetections = await OnnxRuntimeModule.runInferenceFromFrame(
				frameData,
			);

			const processedDetections: BoundingBox[] = [];
			if (rawDetections && Array.isArray(rawDetections)) {
				for (const detection of rawDetections) {
					if (detection.length >= 6 && detection[4] > 0.5) {
						processedDetections.push({
							x: detection[0] - detection[2] / 2,
							y: detection[1] - detection[3] / 2,
							width: detection[2],
							height: detection[3],
							confidence: detection[4],
							class: detection[5],
						});
					}
				}
			}
			setDetections(processedDetections);
		} catch (err) {
			console.error('Frame processing error:', err);
		} finally {
			setIsProcessing(false);
		}
	}, [isInitialized, isProcessing]);

	useEffect(() => {
		if (!isMounted) return;
		const timer = setTimeout(initializeScene, 300);
		return () => clearTimeout(timer);
	}, [isMounted, initializeScene]);

	useEffect(() => {
		if (!isInitialized) return;
		const intervalId = setInterval(processFrame, 100);
		return () => clearInterval(intervalId);
	}, [isInitialized, processFrame]);

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
			{/* ✅ ใช้ NativeARSceneView แทน View ธรรมดา */}
			<NativeARSceneView
				ref={viewRef}
				style={arSceneView.sceneView}
				onLayout={handleViewLayout}
				collapsable={false}
			/>
			<View style={arSceneView.overlayContainer}>
				{renderDetectionOverlays()}
			</View>
			<View style={arSceneView.controlPanel}>
				<TouchableOpacity style={arSceneView.closeButton} onPress={onClose}>
					<Text style={arSceneView.closeButtonText}>Close AR</Text>
				</TouchableOpacity>
				<View style={arSceneView.statusContainer}>
					<Text style={arSceneView.statusText}>
						{isInitialized
							? '✅ AR Active'
							: isMounted
							? '⏳ Initializing...'
							: '📱 Setting up view...'}
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
