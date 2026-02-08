import { Image, StyleSheet, Text, View } from 'react-native';

import type { AudioProTrack } from 'react-native-audio-pro';

interface TrackInfoProps {
	track: AudioProTrack | null;
}

export const TrackInfo: React.FC<TrackInfoProps> = ({ track }) => {
	if (!track) return null;

	const artworkSource =
		typeof track.artwork === 'number' ? track.artwork : { uri: track.artwork };

	return (
		<View style={styles.container}>
			<Image source={artworkSource} style={styles.artwork} />
			<Text style={styles.title}>{track.title}</Text>
			<Text style={styles.artist}>{track.artist}</Text>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		alignItems: 'center',
		marginBottom: 20,
	},
	artwork: {
		width: 250,
		height: 250,
		borderRadius: 8,
		marginBottom: 20,
		backgroundColor: '#333',
	},
	title: {
		fontSize: 24,
		fontWeight: 'bold',
		color: '#FFFFFF',
		marginBottom: 5,
		textAlign: 'center',
	},
	artist: {
		fontSize: 18,
		color: '#CCCCCC',
		textAlign: 'center',
	},
});
