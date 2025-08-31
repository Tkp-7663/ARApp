import React, { useCallback } from 'react';
import { View, Text, TouchableOpacity, NativeModules } from 'react-native';
import { homeStyles } from '../styles/screenStyles';

const { ARLauncher } = NativeModules;

const HomeScreen: React.FC = () => {
	const openAR = useCallback(async () => {
		try {
			if (!ARLauncher || typeof ARLauncher.openARActivity !== 'function') {
				throw new Error('ARLauncher native module not available');
			}
			await ARLauncher.openARActivity();
		} catch (err) {
			console.error('❌ Failed to open AR Activity:', err);
		}
	}, []);

	return (
		<View style={homeStyles.container}>
			<Text style={homeStyles.label}>Demo App Test on S23 Ultra</Text>
			<TouchableOpacity style={homeStyles.button} onPress={openAR}>
				<Text style={homeStyles.buttonText}>Open AR Scene</Text>
			</TouchableOpacity>
		</View>
	);
};

export default HomeScreen;
