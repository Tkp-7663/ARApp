import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
	View,
	TouchableOpacity,
	Text,
	NativeModules,
	findNodeHandle,
	InteractionManager,
	PermissionsAndroid,
	Platform,
	Alert,
} from 'react-native';
import { yoloInference } from '../utils/yoloInference.tsx';
import { arSceneView } from '../styles/componentStyles.ts';

interface BoundingBox {
	x: number;
	y: number;
	width: number;
	height: number;
	confidence: number;
	class: number;
}

interface ARSceneViewProps {
	onClose: () => void;
	onError: (error: string) => void;
}

const { ARCoreModule, SceneViewModule } = NativeModules;

const ARSceneView = ({ onClose, onError }: ARSceneViewProps) => {
	const sceneViewRef = useRef<View>(null);
	const [isInitialized, setIsInitialized] = useState(false);
	const [detections, setDetections] = useState<BoundingBox[]>([]);
	const [isProcessing, setIsProcessing] = useState(false);
	const [isMounted, setIsMounted] = useState(false);

	// ขอสิทธิ์กล้อง
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
				}
			);

			if (granted === PermissionsAndroid.RESULTS.GRANTED) {
				console.log('Camera permission granted');
				return true;
			} else {
				console.log('Camera permission denied');
				Alert.alert(
					'Permission Required',
					'Camera permission is required for AR functionality.',
					[{ text: 'OK', onPress: onClose }]
				);
				return false;
			}
		} catch (err) {
			console.warn('Permission error:', err);
			return false;
		}
	}, [onClose]);

	// ตรวจสอบว่า view mount แล้วหรือยัง
	const handleViewLayout = useCallback(() => {
		if (!isMounted) {
			setIsMounted(true);
		}
	}, [isMounted]);

	// Initialize AR session
	useEffect(() => {
		if (!isMounted) return; // รอจนกว่า view จะ mount เสร็จ

		const initializeAR = async () => {
			try {
				// ขอสิทธิ์กล้องก่อน
				const hasPermission = await requestCameraPermission();
				if (!hasPermission) {
					throw new Error('Camera permission denied');
				}

				// รอให้ interaction เสร็จสิ้น
				await new Promise<void>(resolve =>
					InteractionManager.runAfterInteractions(() => resolve())
				);

				// รอเพิ่มเติมเพื่อให้แน่ใจว่า view พร้อม
				await new Promise<void>(resolve => setTimeout(resolve, 100));

				const viewHandle = findNodeHandle(sceneViewRef.current);
				console.log('View handle:', viewHandle); // Debug log
				
				if (!viewHandle) {
					throw new Error('Scene view not found - view handle is null');
				}

				// Initialize ARCore
				const arInitialized = await ARCoreModule.initializeAR();
				if (!arInitialized) {
					throw new Error('Failed to initialize ARCore');
				}

				// Initialize SceneView
				await SceneViewModule.initializeScene(viewHandle);

				// Start AR session
				await ARCoreModule.startARSession();

				setIsInitialized(true);
				startFrameProcessing();
			} catch (error) {
				console.error('AR Initialization error:', error); // Debug log
				onError(
					`AR Initialization failed: ${
						error instanceof Error ? error.message : String(error)
					}`
				);
			}
		};

		// เพิ่ม delay เล็กน้อยก่อนเริ่ม initialization
		const timeoutId = setTimeout(initializeAR, 200);
		
		return () => {
			clearTimeout(timeoutId);
		};
	}, [isMounted, onError]);

	// Cleanup effect แยกต่างหาก
	useEffect(() => {
		return () => {
			// ตรวจสอบ null ก่อนเรียก
			ARCoreModule.stopARSession?.();
			if (SceneViewModule && typeof SceneViewModule.cleanup === 'function') {
				SceneViewModule.cleanup();
			}
		};
	}, []);

	const startFrameProcessing = useCallback(() => {
		const processFrame = async () => {
			if (!isInitialized || isProcessing) return;

			setIsProcessing(true);
			try {
				// Capture frame from ARCore
				const frameData = await ARCoreModule.captureFrame();
				if (!frameData) return;

				// Run YOLO inference
				const detectionResults = await yoloInference.detectWheels(frameData);
				setDetections(detectionResults);

				// Process each detection for AR placement
				for (const detection of detectionResults) {
					if (detection.confidence > 0.7) {
						const centerX = detection.x + detection.width / 2;
						const centerY = detection.y + detection.height / 2;

						const pose6DoF = await ARCoreModule.hitTestWithOffset(
							centerX,
							centerY,
							0.05 // 5cm offset
						);

						if (pose6DoF) {
							await SceneViewModule.renderBlueBox(pose6DoF);
						}
					}
				}
			} catch (error) {
				console.error('Frame processing error:', error);
			} finally {
				setIsProcessing(false);
			}
		};

		// Process frames at 10 FPS
		const intervalId = setInterval(processFrame, 100);
		return () => clearInterval(intervalId);
	}, [isInitialized, isProcessing]);

	const renderDetectionOverlays = () => {
		return detections.map((detection, index) => (
			<View
				key={index}
				style={[
					arSceneView.boundingBox,
					{
						left: detection.x,
						top: detection.y,
						width: detection.width,
						height: detection.height,
					},
				]}
			>
				<Text style={arSceneView.confidenceText}>
					{(detection.confidence * 100).toFixed(1)}%
				</Text>
			</View>
		));
	};

	return (
		<View style={arSceneView.container}>
			<View 
				ref={sceneViewRef} 
				style={arSceneView.sceneView}
				onLayout={handleViewLayout}
				collapsable={false} // ป้องกันการ optimize view ออก
			/>
			<View style={arSceneView.overlayContainer}>{renderDetectionOverlays()}</View>
			<View style={arSceneView.controlPanel}>
				<TouchableOpacity style={arSceneView.closeButton} onPress={onClose}>
					<Text style={arSceneView.closeButtonText}>Close AR</Text>
				</TouchableOpacity>
				<View style={arSceneView.statusContainer}>
					<Text style={arSceneView.statusText}>
						{isInitialized ? '✅ AR Active' : isMounted ? '⏳ Initializing...' : '📱 Setting up view...'}
					</Text>
					<Text style={arSceneView.statusText}>Detections: {detections.length}</Text>
				</View>
			</View>
		</View>
	);
};

export default ARSceneView;