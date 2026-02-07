import { Dimensions, StyleSheet } from 'react-native';

const { width, height } = Dimensions.get('window');

const aspectRatio = height / width;

const needsSmallerUI = width < 400 || height < 700 || aspectRatio > 1.9;

const scale = needsSmallerUI ? (aspectRatio > 2 ? 0.8 : 0.85) : 1;

let artworkScale = 1;
if (needsSmallerUI) {
	if (aspectRatio > 2.1)
		artworkScale = 0.7; // Very tall phones
	else if (aspectRatio > 1.9)
		artworkScale = 0.75; // Tall phones like Pixel
	else artworkScale = 0.8; // Other small devices
}

console.log(
	`Screen: ${width}x${height}, Ratio: ${aspectRatio.toFixed(2)}, Small UI: ${needsSmallerUI}`,
);

export const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: '#222',
	},
	scrollContent: {
		flexGrow: 1,
		alignItems: 'center',
		justifyContent: 'center',
		padding: needsSmallerUI ? 12 : 20,
	},
	artwork: {
		width: 360 * artworkScale, // Increased by 20%
		height: 360 * artworkScale, // Increased by 20%
		marginBottom: needsSmallerUI ? 12 : 20,
		borderRadius: 15,
	},
	title: {
		fontSize: 24 * scale,
		fontWeight: 'bold',
		color: '#fff',
		marginBottom: needsSmallerUI ? 2 : 4,
	},
	artist: {
		fontSize: 18 * scale,
		color: '#ccc',
		marginBottom: needsSmallerUI ? 12 : 20,
	},

	trackPosition: {
		fontSize: 14 * scale,
		color: '#ccc',
		marginBottom: needsSmallerUI ? 5 : 10,
	},
	sliderContainer: {
		flexDirection: 'row',
		alignItems: 'center',
		width: '100%',
		marginBottom: needsSmallerUI ? 12 : 20,
		maxWidth: 340,
	},
	slider: {
		flex: 1,
		height: needsSmallerUI ? 28 : 40,
	},
	timeText: {
		fontSize: 12 * scale,
		color: '#ccc',
		width: needsSmallerUI ? 32 : 40,
		textAlign: 'center',
	},
	controlsRow: {
		flexDirection: 'row',
		justifyContent: 'space-around',
		alignItems: 'center',
		width: '100%',
		marginBottom: needsSmallerUI ? 12 : 20,
		maxWidth: 300,
	},
	seekRow: {
		flexDirection: 'row',
		justifyContent: 'space-around',
		width: needsSmallerUI ? '75%' : '60%',
		marginBottom: needsSmallerUI ? 12 : 20,
		maxWidth: 220,
	},
	speedRow: {
		flexDirection: 'row',
		justifyContent: 'space-around',
		alignItems: 'center',
		width: needsSmallerUI ? '90%' : '80%',
		marginBottom: needsSmallerUI ? 12 : 20,
		maxWidth: 120,
	},
	generalRow: {
		flexDirection: 'row',
		justifyContent: 'space-around',
		alignItems: 'center',
		width: needsSmallerUI ? '90%' : '80%',
		marginBottom: needsSmallerUI ? 12 : 20,
		maxWidth: 300,
	},
	speedText: {
		fontSize: 18 * scale,
		fontWeight: 'bold',
		color: '#1EB1FC',
		width: needsSmallerUI ? 55 : 70,
		textAlign: 'center',
	},
	stopRow: {
		marginBottom: needsSmallerUI ? 12 : 20,
		flexDirection: 'row',
		justifyContent: 'space-around',
		alignItems: 'center',
	},
	controlText: {
		fontSize: 18 * scale,
		padding: needsSmallerUI ? 6 : 10,
		color: '#fff',
	},
	playPauseText: {
		fontSize: 22 * scale,
		fontWeight: 'bold',
		color: '#fff',
	},
	loadingContainer: {
		width: 80,
		height: 40,
		justifyContent: 'center',
		alignItems: 'center',
	},
	stateText: {
		fontSize: 16 * scale,
		color: '#fff',
		marginBottom: needsSmallerUI ? 2 : 4,
	},
	optionRow: {
		flexDirection: 'row',
		alignItems: 'center',
		justifyContent: 'space-between',
		width: needsSmallerUI ? '60%' : '50%',
		marginBottom: needsSmallerUI ? 12 : 20,
		paddingHorizontal: 10,
	},
	optionText: {
		fontSize: 16 * scale,
		color: '#fff',
		marginRight: 10,
	},
	errorContainer: {
		marginHorizontal: 20,
		marginBottom: needsSmallerUI ? 12 : 20,
		padding: needsSmallerUI ? 10 : 15,
		backgroundColor: '#500',
		borderRadius: 8,
		borderWidth: 1,
		borderColor: '#800',
	},
	errorText: {
		color: '#fff',
		fontSize: 16 * scale,
		marginBottom: needsSmallerUI ? 6 : 10,
	},
	errorButton: {
		backgroundColor: '#800',
		paddingVertical: needsSmallerUI ? 6 : 8,
		paddingHorizontal: needsSmallerUI ? 8 : 12,
		borderRadius: 4,
		alignSelf: 'flex-start',
	},
	errorButtonText: {
		color: 'white',
		fontSize: 14 * scale,
		fontWeight: 'bold',
	},
	// Settings styles
	settingsContainer: {
		marginHorizontal: needsSmallerUI ? 10 : 20,
		marginBottom: needsSmallerUI ? 12 : 20,
		paddingVertical: needsSmallerUI ? 10 : 15,
		paddingHorizontal: needsSmallerUI ? 15 : 20,
		backgroundColor: '#333',
		borderRadius: 8,
	},
	settingsHeader: {
		fontSize: 18 * scale,
		fontWeight: 'bold',
		color: '#fff',
		marginBottom: needsSmallerUI ? 10 : 15,
	},
	settingRow: {
		flexDirection: 'row',
		justifyContent: 'space-between',
		alignItems: 'center',
		marginBottom: needsSmallerUI ? 8 : 12,
	},
	settingLabel: {
		fontSize: 16 * scale,
		color: '#ccc',
	},
	contentTypeButton: {
		backgroundColor: '#1EB1FC',
		paddingVertical: needsSmallerUI ? 4 : 6,
		paddingHorizontal: needsSmallerUI ? 8 : 12,
		borderRadius: 4,
	},
	contentTypeButtonText: {
		color: 'white',
		fontSize: 14 * scale,
		fontWeight: 'bold',
	},
	// Ambient audio styles
	ambientSection: {
		width: '100%',
		alignItems: 'center',
	},
	sectionTitle: {
		fontSize: 18 * scale,
		fontWeight: 'bold',
		color: '#1EB1FC',
		marginBottom: needsSmallerUI ? 10 : 15,
	},
	controlIcon: {
		width: 35 * scale,
		height: 35 * scale,
		tintColor: '#fff',
	},
	playPauseButton: {
		backgroundColor: '#1EB1FC',
		borderRadius: 50,
		padding: 15,
		alignItems: 'center',
		justifyContent: 'center',
		elevation: 5,
		shadowColor: '#000',
		shadowOffset: { width: 0, height: 2 },
		shadowOpacity: 0.3,
		shadowRadius: 3,
	},
	playPauseIcon: {
		width: 30 * scale,
		height: 30 * scale,
		tintColor: '#fff',
	},
});
