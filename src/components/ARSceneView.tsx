import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
	View,
	StyleSheet,
	TouchableOpacity,
	Text,
	NativeModules,
	findNodeHandle,
} from 'react-native';
import { yoloInference } from '../utils/yoloInference.tsx';

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
	const sceneViewRef = useRef(null);
	const [isInitialized, setIsInitialized] = useState(false);
	const [detections, setDetections] = useState<BoundingBox[]>([]);
	const [isProcessing, setIsProcessing] = useState(false);

	// Initialize AR session
	useEffect(() => {
		const initializeAR = async () => {
			try {
				const viewHandle = findNodeHandle(sceneViewRef.current);
				if (!viewHandle) {
					throw new Error('Scene view not found');
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
				onError(
					`AR Initialization failed: ${
						error instanceof Error ? error.message : String(error)
					}`,
				);
			}
		};

		initializeAR();

		return () => {
			ARCoreModule.stopARSession?.();
			SceneViewModule.cleanup?.();
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
						// Calculate center point with offset
						const centerX = detection.x + detection.width / 2;
						const centerY = detection.y + detection.height / 2;

						// Get 6DoF pose from hit test
						const pose6DoF = await ARCoreModule.hitTestWithOffset(
							centerX,
							centerY,
							0.05, // 5cm offset
						);

						if (pose6DoF) {
							// Render blue AR box at 6DoF position
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
					styles.boundingBox,
					{
						left: detection.x,
						top: detection.y,
						width: detection.width,
						height: detection.height,
					},
				]}
			>
				<Text style={styles.confidenceText}>
					{(detection.confidence * 100).toFixed(1)}%
				</Text>
			</View>
		));
	};

	return (
		<View style={styles.container}>
			{/* AR Scene View */}
			<View ref={sceneViewRef} style={styles.sceneView} />

			{/* Detection Overlays */}
			<View style={styles.overlayContainer}>{renderDetectionOverlays()}</View>

			{/* Control Panel */}
			<View style={styles.controlPanel}>
				<TouchableOpacity style={styles.closeButton} onPress={onClose}>
					<Text style={styles.closeButtonText}>Close AR</Text>
				</TouchableOpacity>

				<View style={styles.statusContainer}>
					<Text style={styles.statusText}>
						{isInitialized ? '✅ AR Active' : '⏳ Initializing...'}
					</Text>
					<Text style={styles.statusText}>Detections: {detections.length}</Text>
				</View>
			</View>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: 'black',
	},
	sceneView: {
		flex: 1,
	},
	overlayContainer: {
		position: 'absolute',
		top: 0,
		left: 0,
		right: 0,
		bottom: 0,
	},
	boundingBox: {
		position: 'absolute',
		borderWidth: 2,
		borderColor: 'red',
		backgroundColor: 'transparent',
	},
	confidenceText: {
		color: 'red',
		fontSize: 12,
		fontWeight: 'bold',
		backgroundColor: 'rgba(255,255,255,0.8)',
		paddingHorizontal: 4,
		paddingVertical: 2,
	},
	controlPanel: {
		position: 'absolute',
		bottom: 30,
		left: 20,
		right: 20,
		flexDirection: 'row',
		justifyContent: 'space-between',
		alignItems: 'center',
	},
	closeButton: {
		backgroundColor: 'rgba(255,0,0,0.8)',
		paddingHorizontal: 20,
		paddingVertical: 10,
		borderRadius: 20,
	},
	closeButtonText: {
		color: 'white',
		fontWeight: 'bold',
	},
	statusContainer: {
		alignItems: 'flex-end',
	},
	statusText: {
		color: 'white',
		fontSize: 12,
		backgroundColor: 'rgba(0,0,0,0.6)',
		paddingHorizontal: 8,
		paddingVertical: 4,
		borderRadius: 8,
		marginVertical: 2,
	},
});

export default ARSceneView;
