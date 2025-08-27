import React, { useState } from 'react';
import {
	View,
	Text,
	TouchableOpacity,
	Alert,
	PermissionsAndroid,
	Platform,
} from 'react-native';
import ARSceneViewMinimal from '../components/ARSceneViewMinimal'; // ใช้ตัว Minimal
import { homeScreen } from '../styles/screenStyles';

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

		setIsARActive(true);
	};

	const handleStopAR = () => {
		setIsARActive(false);
	};

	if (isARActive) {
		return (
			<ARSceneViewMinimal
				onClose={handleStopAR}
				onError={error => {
					Alert.alert('AR Error', error);
					setIsARActive(false);
				}}
			/>
		);
	}

	return (
		<View style={homeScreen.container}>
			<Text style={homeScreen.title}>AR Alloy Wheel Detection</Text>
			<Text style={homeScreen.subtitle}>Demo on Samsung Galaxy S23 Ultra</Text>

			<TouchableOpacity style={homeScreen.startButton} onPress={handleStartAR}>
				<Text style={homeScreen.buttonText}>Start AR Detection</Text>
			</TouchableOpacity>
		</View>
	);
};

export default HomeScreen;
