import type { AudioProEqualizerPreset } from '../types';

/**
 * Equalizer Bands configuration matches the user provided reference.
 * 10 Bands: 31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz
 */
export const EQUALIZER_BANDS = [
	{ frequency: 31, label: '31Hz' },
	{ frequency: 63, label: '63Hz' },
	{ frequency: 125, label: '125Hz' },
	{ frequency: 250, label: '250Hz' },
	{ frequency: 500, label: '500Hz' },
	{ frequency: 1000, label: '1kHz' },
	{ frequency: 2000, label: '2kHz' },
	{ frequency: 4000, label: '4kHz' },
	{ frequency: 8000, label: '8kHz' },
	{ frequency: 16000, label: '16kHz' },
];

export const EQUALIZER_PRESETS: AudioProEqualizerPreset[] = [
	{
		name: 'Default',
		id: 'default',
		gains: [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
		description: 'Neutral sound with no frequency adjustments.',
	},
	{
		name: 'Club',
		id: 'club',
		gains: [0.0, 0.0, 4.8, 3.36, 3.36, 3.36, 1.92, 0.0, 0.0, 0.0],
	},
	{
		name: 'Live',
		id: 'live',
		gains: [-2.88, 0.0, 2.4, 3.36, 3.36, 3.36, 2.4, 1.44, 1.44, 1.44],
	},
	{
		name: 'Party',
		id: 'Party',
		gains: [4.32, 4.32, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 4.32, 4.32],
	},
	{
		name: 'Pop',
		id: 'pop',
		gains: [0.96, 2.88, 4.32, 4.8, 3.36, 0.0, -1.44, -1.44, 0.96, 0.96],
	},
	{
		name: 'Soft',
		id: 'soft',
		gains: [2.88, 0.96, 0.0, -1.44, 0.0, 2.4, 4.8, 5.76, 6.72, 7.2],
	},
	{
		name: 'Ska',
		id: 'ska',
		gains: [-1.44, -2.88, -2.4, 0.0, 2.4, 3.36, 5.28, 5.76, 6.72, 5.76],
	},
	{
		name: 'Reggae',
		id: 'reggae',
		gains: [0.0, 0.0, 0.0, -3.36, 0.0, 3.84, 3.84, 0.0, 0.0, 0.0],
	},
	{
		name: 'Rock',
		id: 'rock',
		gains: [4.8, 2.88, -3.36, -4.8, -1.92, 2.4, 5.28, 6.72, 6.72, 6.72],
	},
	{
		name: 'Dance',
		id: 'dance',
		gains: [5.76, 4.32, 1.44, 0.0, 0.0, -3.36, -4.32, -4.32, 0.0, 0.0],
	},
	{
		name: 'Techno',
		id: 'techno',
		gains: [4.8, 3.36, 0.0, -3.36, -2.88, 0.0, 4.8, 5.76, 5.76, 5.28],
	},
	{
		name: 'Headphones',
		id: 'headphones',
		gains: [2.88, 6.72, 3.36, -1.92, -1.44, 0.96, 2.88, 5.76, 7.68, 8.64],
	},
	{
		name: 'Soft rock',
		id: 'soft_rock',
		gains: [2.4, 2.4, 1.44, 0.0, -2.4, -3.36, -1.92, 0.0, 1.44, 5.28],
	},
	{
		name: 'Classical',
		id: 'classical',
		gains: [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -4.32, -4.32, -4.32, -5.76],
	},
	{
		name: 'Large Hall',
		id: 'large_hall',
		gains: [6.24, 6.24, 3.36, 3.36, 0.0, -2.88, -2.88, -2.88, 0.0, 0.0],
	},
	{
		name: 'Full Bass',
		id: 'full_base',
		gains: [4.8, 5.76, 5.76, 3.36, 0.96, -2.4, -4.8, -6.24, -6.72, -6.72],
	},
	{
		name: 'Full Treble',
		id: 'full_treble',
		gains: [-5.76, -5.76, -5.76, -2.4, 1.44, 6.72, 9.6, 9.6, 9.6, 10.08],
	},
	{
		name: 'Laptop Speakers',
		id: 'laptop_speakers',
		gains: [2.88, 6.72, 3.36, -1.92, -1.44, 0.96, 2.88, 5.76, 7.68, 8.64],
	},
	{
		name: 'Full Bass & Treble',
		id: 'bass_treble',
		gains: [4.32, 3.36, 0.0, -4.32, -2.88, 0.96, 4.8, 6.72, 7.2, 7.2],
	},
];

export const EQUALIZER_ADVANCED_PRESETS: AudioProEqualizerPreset[] = [
	{
		id: 'adv-flat',
		name: 'Flat (Adv)',
		gains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
		description:
			'Neutral sound with no frequency adjustments. Perfect for well-recorded music and reference listening.',
	},
	{
		id: 'adv-rock',
		name: 'Rock (Adv)',
		gains: [4, 2, -1, -2, -1, 1, 4, 5, 3, 2],
		description:
			'Enhanced drums and guitars with clear highs. Perfect for rock, metal, and guitar-driven music.',
	},
	{
		id: 'adv-pop',
		name: 'Pop (Adv)',
		gains: [2, 3, 4, 3, 1, -1, 3, 4, 3, 2],
		description:
			'Vibrant and punchy sound with enhanced vocals. Ideal for modern pop and mainstream music.',
	},
	{
		id: 'adv-jazz',
		name: 'Jazz (Adv)',
		gains: [3, 2, 1, 3, -1, -1, 1, 2, 3, 3],
		description:
			'Rich and warm tone emphasizing acoustic instruments. Perfect for jazz, blues, and live recordings.',
	},
	{
		id: 'adv-classical',
		name: 'Classical (Adv)',
		gains: [3, 2, 1, 1, -1, -1, 1, 3, 4, 5],
		description:
			'Wide dynamic range with clear highs and controlled mids. Ideal for orchestral and chamber music.',
	},
	{
		id: 'adv-hip-hop',
		name: 'Hip-Hop (Adv)',
		gains: [6, 4, 2, 0, -1, -1, 1, 3, 4, 3],
		description:
			'Deep bass with crisp highs for impact. Perfect for hip-hop, rap, and bass-heavy electronic music.',
	},
	{
		id: 'adv-electronic',
		name: 'Electronic (Adv)',
		gains: [4, 3, 1, -1, 2, 3, 2, 4, 6, 4],
		description:
			'Enhanced low-end and sparkling highs. Designed for EDM, techno, and synthesized music.',
	},
	{
		id: 'adv-bass-boost',
		name: 'Bass Boost (Adv)',
		gains: [7, 5, 3, 1, 0, 0, 0, 0, 0, 0],
		description:
			'Strong low-frequency enhancement for bass impact. Great for bass lovers and small speakers.',
	},
	{
		id: 'adv-vocal-clarity',
		name: 'Vocal Clarity (Adv)',
		gains: [-2, -1, 1, 4, 5, 5, 4, 3, 1, 0],
		description:
			'Enhanced mid-range for crystal clear vocals. Perfect for podcasts, audiobooks, and vocal-focused music.',
	},
	{
		id: 'adv-treble-boost',
		name: 'Treble Boost (Adv)',
		gains: [0, 0, 0, 1, 2, 3, 5, 6, 7, 5],
		description:
			'Brightened highs for detail and air. Ideal for acoustic music and revealing hidden details.',
	},
	{
		id: 'adv-studio-monitor',
		name: 'Studio Monitor (Adv)',
		gains: [1, 0, -1, 0, 2, 2, 1, 2, 3, 1],
		description:
			'Professional reference tuning for accurate monitoring. Reveals details without coloration.',
	},
	{
		id: 'adv-audiophile',
		name: 'Audiophile (Adv)',
		gains: [2, 1, 0, 1, -1, 1, 2, 3, 3, 2],
		description:
			'Refined and balanced sound for critical listening. Emphasizes naturalness and detail retrieval.',
	},
	{
		id: 'adv-headphones',
		name: 'Headphones (Adv)',
		gains: [3, 2, 1, 2, 3, 3, 2, 3, 4, 3],
		description:
			'Optimized for over-ear and on-ear headphones. Compensates for typical headphone characteristics.',
	},
	{
		id: 'adv-earbuds',
		name: 'Earbuds (Adv)',
		gains: [4, 3, 1, 1, 2, 3, 4, 5, 4, 3],
		description:
			'Tuned for in-ear monitors and earbuds. Enhances detail while maintaining comfort.',
	},
	{
		id: 'adv-speakers',
		name: 'Speakers (Adv)',
		gains: [2, 1, 1, 3, 2, 1, 2, 3, 3, 2],
		description:
			'Balanced for desktop and bookshelf speakers. Optimizes room acoustics and speaker placement.',
	},
	{
		id: 'adv-live-concert',
		name: 'Live Concert (Adv)',
		gains: [3, 2, 1, 2, 3, 2, 4, 5, 4, 3],
		description:
			'Recreates live venue atmosphere with enhanced presence. Great for concert recordings and live albums.',
	},
	{
		id: 'adv-midnight-mode',
		name: 'Midnight Mode (Adv)',
		gains: [-3, -1, 1, 4, 3, 3, 2, 1, -1, -2],
		description:
			'Compressed dynamic range for late-night listening. Reduces loud peaks while maintaining clarity.',
	},
	{
		id: 'adv-workout',
		name: 'Workout (Adv)',
		gains: [5, 4, 3, 1, 2, 3, 5, 4, 3, 2],
		description:
			'Energetic and motivating sound with punchy bass. Perfect for gym sessions and high-energy activities.',
	},
	{
		id: 'adv-warm-analog',
		name: 'Warm Analog (Adv)',
		gains: [4, 3, 1, 1, -1, 1, 2, 3, 2, 1],
		description:
			'Rich vintage warmth with analog character. Adds musical coloration reminiscent of tape and tube equipment.',
	},
	{
		id: 'adv-crystal-clear',
		name: 'Crystal Clear (Adv)',
		gains: [1, 1, 2, 3, 4, 5, 6, 5, 4, 3],
		description:
			'Enhanced highs with exceptional detail. Perfect for revealing hidden elements in your music.',
	},
	{
		id: 'adv-rich-full',
		name: 'Rich & Full (Adv)',
		gains: [4, 3, 2, 2, 3, 4, 3, 4, 3, 2],
		description:
			'Full-bodied sound with rich harmonics. Adds weight and presence to all frequencies for immersive listening.',
	},
	{
		id: 'adv-atmospheric',
		name: 'Atmospheric (Adv)',
		gains: [3, 2, 1, 2, 1, 2, 4, 5, 6, 5],
		description:
			'Spacious and airy soundscape. Creates depth and ambience perfect for ambient and cinematic music.',
	},
	{
		id: 'adv-vibrant-pop',
		name: 'Vibrant Pop (Adv)',
		gains: [3, 4, 5, 4, 2, 3, 5, 6, 5, 4],
		description:
			'Colorful and exciting sound with enhanced energy. Makes pop music sparkle with life and vibrancy.',
	},
	{
		id: 'adv-deep-impact',
		name: 'Deep Impact (Adv)',
		gains: [8, 6, 4, 2, 1, 1, 3, 4, 5, 4],
		description:
			'Strong low-end extension with controlled power. Creates cinematic impact for movies and epic music.',
	},
	{
		id: 'adv-silk-smooth',
		name: 'Silk Smooth (Adv)',
		gains: [2, 2, 1, 2, 3, 3, 2, 1, -1, -2],
		description:
			'Ultra-smooth and refined sound. Removes harshness while maintaining musicality for extended listening.',
	},
	{
		id: 'adv-presence-boost',
		name: 'Presence Boost (Adv)',
		gains: [1, 1, 2, 3, 5, 6, 5, 4, 3, 2],
		description:
			'Forward and intimate sound with enhanced presence. Brings vocals and instruments closer to the listener.',
	},
];
