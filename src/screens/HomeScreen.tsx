import React, { useState } from 'react';
import {
	View,
	Text,
	TouchableOpacity,
	StyleSheet,
	Alert,
	PermissionsAndroid,
	Platform,
} from 'react-native';
import ARSceneView from '../components/ARSceneView';

const HomeScreen = () => {
	const [isARActive, setIsARActive] = useState(false);

	const requestCameraPermission = async () => {
		if (Platform.OS === 'android') {
			try {
				const granted = await PermissionsAndroid.request(
					PermissionsAndroid.PERMISSIONS.CAMERA,
					{
						title: 'Camera Permission',
						message: 'This app needs camera permission for AR functionality',
						buttonNeutral: 'Ask Me Later',
						buttonNegative: 'Cancel',
						buttonPositive: 'OK',
					},
				);
				return granted === PermissionsAndroid.RESULTS.GRANTED;
			} catch (err) {
				console.warn(err);
				return false;
			}
		}
		return true;
	};

	const handleStartAR = async () => {
		const hasPermission = await requestCameraPermission();
		if (!hasPermission) {
			Alert.alert('Error', 'Camera permission is required for AR');
			return;
		}

		try {
			setIsARActive(true);
		} catch (error) {
			Alert.alert('Error', 'Failed to start AR session');
			console.error('AR Start Error:', error);
		}
	};

	const handleStopAR = () => {
		setIsARActive(false);
	};

	if (isARActive) {
		return (
			<ARSceneView
				onClose={handleStopAR}
				onError={error => {
					Alert.alert('AR Error', error);
					setIsARActive(false);
				}}
			/>
		);
	}

	return (
		<View style={styles.container}>
			<Text style={styles.title}>AR Alloy Wheel Detection</Text>
			<Text style={styles.subtitle}>Demo on Samsung Galaxy S23 Ultra</Text>
			{/* <Text style={styles.subtitle}>Samsung Galaxy S23 Ultra Demo</Text> */}

			<TouchableOpacity style={styles.startButton} onPress={handleStartAR}>
				<Text style={styles.buttonText}>Start AR Detection</Text>
			</TouchableOpacity>

			<View style={styles.infoContainer}>
				{/* <Text style={styles.infoText}>• Point camera at alloy wheels</Text>
				<Text style={styles.infoText}>
					• AI will detect and highlight wheels
				</Text>
				<Text style={styles.infoText}>• Blue 3D boxes show AR positions</Text> */}
			</View>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: '#1a1a1a',
		justifyContent: 'center',
		alignItems: 'center',
		padding: 20,
	},
	title: {
		fontSize: 28,
		fontWeight: 'bold',
		color: '#ffffff',
		marginBottom: 8,
		textAlign: 'center',
	},
	subtitle: {
		fontSize: 16,
		color: '#888888',
		marginBottom: 40,
	},
	startButton: {
		backgroundColor: '#0066cc',
		paddingHorizontal: 40,
		paddingVertical: 15,
		borderRadius: 25,
		marginBottom: 40,
	},
	buttonText: {
		color: 'white',
		fontSize: 18,
		fontWeight: '600',
	},
	infoContainer: {
		alignItems: 'flex-start',
	},
	infoText: {
		color: '#cccccc',
		fontSize: 14,
		marginBottom: 8,
	},
});

export default HomeScreen;
