/**
 * Play pronunciation audio for an English word.
 * Strategy A: Youdao dictionary free API
 * Strategy B: Browser SpeechSynthesis fallback
 */

let currentAudio = null

export function playWord(word) {
  if (!word) return
  const text = word.trim().toLowerCase()

  // Stop any currently playing audio
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }

  // Strategy A: Youdao free pronunciation URL
  const url = `https://dict.youdao.com/dictvoice?audio=${encodeURIComponent(text)}&type=1`
  const audio = new Audio(url)
  currentAudio = audio

  audio.play().catch(() => {
    // Strategy B: fallback to browser SpeechSynthesis
    if ('speechSynthesis' in window) {
      speechSynthesis.cancel()
      const utterance = new SpeechSynthesisUtterance(text)
      utterance.lang = 'en-US'
      utterance.rate = 0.9
      speechSynthesis.speak(utterance)
    }
  })
}
