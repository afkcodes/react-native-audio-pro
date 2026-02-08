
import { useEffect } from 'react';

import { AudioPro, type AudioProTrack } from 'react-native-audio-pro';

/**
 * Headless component that handles "Sliding Window" URL refreshing
 * and error recovery logic.
 */
export const URLRefreshLogic: React.FC = () => {
	useEffect(() => {
		const refreshTrackUrl = async (index: number, track: AudioProTrack) => {
			if (!track || !track.id) return;

			// eslint-disable-next-line @typescript-eslint/no-explicit-any
			const source = (track as any).source || 'gaana';
			const apiUrl = `https://api.sunoh.online/music/song/${track.id}/stream?provider=${source}`;

			// console.log(`[RefreshLogic] Fetching new URL for track ${index}: ${track.title}`);

			try {
				const response = await fetch(apiUrl);
				const json = await response.json();

				if (json.status === 'success' && Array.isArray(json.data) && json.data.length > 0) {
					const links = json.data;
					let newUrl = links[0].link;

					// eslint-disable-next-line @typescript-eslint/no-explicit-any
					const high = links.find((l: any) => l.quality === 'high');
					// eslint-disable-next-line @typescript-eslint/no-explicit-any
					const medium = links.find((l: any) => l.quality === 'medium');

					if (high) newUrl = high.link;
					else if (medium) newUrl = medium.link;

					if (newUrl !== track.url) {
						console.log(`[RefreshLogic] Updating track ${index} with new URL`);

						AudioPro.updateTrack(index, {
							...track,
							url: newUrl,
						});
					} else {
						// URL already up to date
					}
				}
			} catch (error) {
				console.error(`[RefreshLogic] Error fetching stream for ${track.id}:`, error);
			}
		};

		const subscription = AudioPro.addEventListener((event) => {
			if (event.type === 'TRACK_CHANGED') {
				const { index } = event.payload || {};
				if (typeof index === 'number') {
					// Refresh Next and Previous tracks (Sliding Window)
					AudioPro.getQueue().then((queue) => {
						const nextIndex = index + 1;
						const prevIndex = index - 1;

						if (nextIndex < queue.length) {
							const nextTrack = queue[nextIndex];
							if (nextTrack) refreshTrackUrl(nextIndex, nextTrack);
						}

						if (prevIndex >= 0) {
							const prevTrack = queue[prevIndex];
							if (prevTrack) refreshTrackUrl(prevIndex, prevTrack);
						}
					});
				}
			} else if (event.type === 'PLAYBACK_ERROR') {
				// Error Recovery
				const { index } = event.payload || {};
				if (typeof index === 'number') {
					console.log(
						`[RefreshLogic] Playback error at index ${index}. Attempting refresh & retry.`,
					);
					AudioPro.getQueue().then(async (queue) => {
						const track = queue[index];
						if (track) {
							await refreshTrackUrl(index, track);
							// Slight delay to ensure native update propagates before skipping
							setTimeout(() => {
								AudioPro.skipTo(index); // Retry
							}, 100);
						}
					});
				}
			}
		});

		return () => {
			subscription.remove();
		};
	}, []);

	return null;
};
