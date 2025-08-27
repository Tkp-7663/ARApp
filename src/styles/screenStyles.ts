import { StyleSheet } from "react-native";

export const homeScreen = StyleSheet.create({
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
