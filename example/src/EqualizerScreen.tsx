import { useEffect, useState } from 'react';

import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import Slider from '@react-native-community/slider';
import {
	AudioPro,
	EQUALIZER_ADVANCED_PRESETS,
	EQUALIZER_BANDS,
	EQUALIZER_PRESETS,
} from 'react-native-audio-pro';

import type { AudioProEqualizerPreset } from 'react-native-audio-pro';

export const EqualizerScreen = () => {
	// 10 bands by default from our constants
	const [gains, setGains] = useState<number[]>(new Array(EQUALIZER_BANDS.length).fill(0));
	const [bassBoost, setBassBoost] = useState(0);
	const [selectedPresetId, setSelectedPresetId] = useState('default');

	useEffect(() => {
		// Apply initial defaults
		AudioPro.setEqualizer(gains);
		AudioPro.setBassBoost(bassBoost);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	const updateGain = (index: number, value: number) => {
		const newGains = [...gains];
		newGains[index] = value;
		setGains(newGains);
		AudioPro.setEqualizer(newGains);
		setSelectedPresetId('custom');
	};

	const applyPreset = (presetId: string) => {
		const preset = [...EQUALIZER_PRESETS, ...EQUALIZER_ADVANCED_PRESETS].find(
			(p) => p.id === presetId,
		);
		if (preset) {
			setGains([...preset.gains]);
			AudioPro.setEqualizer(preset.gains);
			setSelectedPresetId(presetId);
		}
	};

	const updateBassBoost = (value: number) => {
		setBassBoost(value);
		AudioPro.setBassBoost(value);
	};

	return (
		<View style={styles.container}>
			<Text style={styles.title}>Equalizer (10 Bands)</Text>

			<View style={styles.section}>
				<Text style={styles.sectionTitle}>Presets</Text>
				<ScrollView horizontal showsHorizontalScrollIndicator={false}>
					{EQUALIZER_PRESETS.map((preset: AudioProEqualizerPreset) => (
						<TouchableOpacity
							key={preset.id}
							style={[
								styles.presetButton,
								selectedPresetId === preset.id && styles.presetButtonActive,
							]}
							onPress={() => applyPreset(preset.id)}
						>
							<Text
								style={[
									styles.presetText,
									selectedPresetId === preset.id && styles.presetTextActive,
								]}
							>
								{preset.name}
							</Text>
						</TouchableOpacity>
					))}
				</ScrollView>
			</View>

			<View style={styles.section}>
				<Text style={styles.sectionTitle}>Advanced Presets</Text>
				<ScrollView horizontal showsHorizontalScrollIndicator={false}>
					{EQUALIZER_ADVANCED_PRESETS.map((preset: AudioProEqualizerPreset) => (
						<TouchableOpacity
							key={preset.id}
							style={[
								styles.presetButton,
								selectedPresetId === preset.id && styles.presetButtonActive,
							]}
							onPress={() => applyPreset(preset.id)}
						>
							<Text
								style={[
									styles.presetText,
									selectedPresetId === preset.id && styles.presetTextActive,
								]}
							>
								{preset.name}
							</Text>
						</TouchableOpacity>
					))}
				</ScrollView>
				{selectedPresetId !== 'custom' && (
					<View style={styles.descriptionContainer}>
						<Text style={styles.descriptionText}>
							{
								[...EQUALIZER_PRESETS, ...EQUALIZER_ADVANCED_PRESETS].find(
									(p) => p.id === selectedPresetId,
								)?.description
							}
						</Text>
					</View>
				)}
			</View>

			<View style={styles.section}>
				<Text style={styles.sectionTitle}>Bands (dB)</Text>
				{EQUALIZER_BANDS.map((band, index) => (
					<View key={band.frequency} style={styles.bandRow}>
						<Text style={styles.bandLabel}>{band.label}</Text>
						<Slider
							style={styles.slider}
							minimumValue={-10}
							maximumValue={10}
							step={0.1}
							value={gains[index]}
							onValueChange={(val) => updateGain(index, val)}
							minimumTrackTintColor="#1db954"
							maximumTrackTintColor="#555"
							thumbTintColor="#1db954"
						/>
						<Text style={styles.valueLabel}>{gains[index]?.toFixed(1)} dB</Text>
					</View>
				))}
			</View>

			<View style={styles.section}>
				<Text style={styles.sectionTitle}>Bass Boost (0-1000)</Text>
				<View style={styles.bandRow}>
					<Text style={styles.bandLabel}>Strength</Text>
					<Slider
						style={styles.slider}
						minimumValue={0}
						maximumValue={1000}
						step={10}
						value={bassBoost}
						onValueChange={updateBassBoost}
						minimumTrackTintColor="#1db954"
						maximumTrackTintColor="#555"
						thumbTintColor="#1db954"
					/>
					<Text style={styles.valueLabel}>{bassBoost}</Text>
				</View>
			</View>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		padding: 20,
		backgroundColor: '#121212',
		borderRadius: 10,
		marginTop: 10,
		width: '100%',
	},
	title: {
		fontSize: 24,
		fontWeight: 'bold',
		color: '#fff',
		marginBottom: 20,
	},
	section: {
		marginBottom: 30,
	},
	sectionTitle: {
		fontSize: 18,
		fontWeight: '600',
		color: '#ddd',
		marginBottom: 10,
	},
	presetButton: {
		paddingHorizontal: 15,
		paddingVertical: 8,
		borderRadius: 20,
		backgroundColor: '#333',
		marginRight: 10,
		borderWidth: 1,
		borderColor: '#333',
	},
	presetButtonActive: {
		backgroundColor: '#1db954',
		borderColor: '#1db954',
	},
	presetText: {
		color: '#ccc',
		fontSize: 14,
	},
	presetTextActive: {
		color: '#fff',
		fontWeight: 'bold',
	},
	bandRow: {
		flexDirection: 'row',
		alignItems: 'center',
		marginBottom: 15,
	},
	bandLabel: {
		width: 50,
		color: '#bbb',
		fontSize: 12,
	},
	slider: {
		flex: 1,
		height: 40,
		marginHorizontal: 10,
	},
	valueLabel: {
		width: 50,
		textAlign: 'right',
		color: '#bbb',
		fontSize: 12,
	},
	descriptionContainer: {
		marginTop: 15,
		padding: 12,
		backgroundColor: '#222',
		borderRadius: 8,
		borderLeftWidth: 4,
		borderLeftColor: '#1db954',
	},
	descriptionText: {
		color: '#aaa',
		fontSize: 13,
		lineHeight: 18,
		fontStyle: 'italic',
	},
});
