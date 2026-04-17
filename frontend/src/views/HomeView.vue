<template>
  <div class="home-page">
    <NavBar />

    <!-- Hero Section -->
    <section class="hero">
      <div class="container">
        <div class="hero-inner">
          <!-- Left: Copy -->
          <div class="hero-left fade-in-up">
            <div class="tag-badge">
              <span class="tag-dot"></span>
              智能备考平台
            </div>
            <h1 class="hero-title">
              专注备考<br />
              <span class="hero-title-accent">高效提分</span>
            </h1>
            <p class="hero-desc">
              Edge TTS 真人语音 · 10分钟记忆 · 推荐机考界面还原<br />
              上传真题，智能批改，原文高亮定位错误依据。
            </p>
            <div class="hero-actions">
              <router-link to="/register" class="btn-primary">免费注册开始</router-link>
              <span class="login-hint">
                已有账号？
                <router-link to="/login" class="login-link">登录</router-link>
              </span>
            </div>
            <div class="hero-stats">
              <div class="stat-item">
                <span class="stat-num">10</span>
                <span class="stat-lbl">词组</span>
              </div>
              <div class="stat-sep">|</div>
              <div class="stat-item">
                <span class="stat-num">3</span>
                <span class="stat-lbl">题型</span>
              </div>
              <div class="stat-sep">|</div>
              <div class="stat-item">
                <span class="stat-num">∞</span>
                <span class="stat-lbl">词库</span>
              </div>
            </div>
          </div>

          <!-- Right: Word Card -->
          <div class="hero-right fade-in-up" style="animation-delay:0.15s">
            <WordCard
              :word="wordStore.currentWord"
              @know="wordStore.markKnown"
              @unknown="wordStore.markUnknown"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features section">
      <div class="container">
        <h2 class="section-title">完整的备考工具链</h2>
        <p class="section-subtitle">从词汇积累到全真模考，覆盖雅思备考全流程</p>
        <div class="features-grid">
          <div v-for="feat in features" :key="feat.title" class="feature-card card">
            <div class="feat-icon" :style="{ background: feat.iconBg }">
              <span v-html="feat.icon"></span>
            </div>
            <h3 class="feat-title">{{ feat.title }}</h3>
            <p class="feat-desc">{{ feat.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- How it works -->
    <section class="howto section" style="background: var(--bg-white);">
      <div class="container">
        <h2 class="section-title">三步开始备考</h2>
        <p class="section-subtitle">简单高效，立即上手</p>
        <div class="steps-row">
          <div v-for="(step, i) in steps" :key="i" class="step-item">
            <div class="step-num">{{ String(i + 1).padStart(2, '0') }}</div>
            <h3 class="step-title">{{ step.title }}</h3>
            <p class="step-desc">{{ step.desc }}</p>
          </div>
          <div class="step-line"></div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta section">
      <div class="container">
        <div class="cta-card">
          <h2 class="cta-title">准备好开始了吗？</h2>
          <p class="cta-desc">免费注册，立即体验全部功能</p>
          <router-link to="/register" class="btn-primary cta-btn">免费注册开始</router-link>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
      <div class="container">
        <div class="footer-inner">
          <div class="footer-brand">
            <span class="logo-icon">✦</span>
            <span class="logo-text">IELTS Studio</span>
          </div>
          <p class="footer-copy">© 2024 IELTS Studio. 专注雅思备考。</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import NavBar from '@/components/NavBar.vue'
import WordCard from '@/components/WordCard.vue'
import { useWordStore } from '@/stores/word'

const wordStore = useWordStore()

const features = [
  {
    title: '智能背单词',
    desc: 'Edge TTS 真人发音，艾宾浩斯遗忘曲线算法，10分钟高效记忆雅思核心词汇。',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
    iconBg: 'linear-gradient(135deg, #1B4332, #2D6A4F)',
  },
  {
    title: '真题模拟考试',
    desc: '严格还原雅思机考界面，支持上传PDF/Word试卷，自动解析生成可在线作答的电子试卷。',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>',
    iconBg: 'linear-gradient(135deg, #1D4ED8, #3B82F6)',
  },
  {
    title: '智能批改分析',
    desc: '提交后自动批改，实时展示总分及各题型得分，错题自动在原文中标红高亮定位句。',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>',
    iconBg: 'linear-gradient(135deg, #B45309, #D97706)',
  },
  {
    title: '错题本',
    desc: '自动归集历次错题，按题型/试卷分类管理，支持重复练习，精准攻克弱项。',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>',
    iconBg: 'linear-gradient(135deg, #7C3AED, #A78BFA)',
  },
]

const steps = [
  {
    title: '注册账号',
    desc: '免费注册，30秒完成，立即开始备考之旅。',
  },
  {
    title: '上传真题',
    desc: '上传PDF或Word格式试卷，系统自动解析生成在线试卷。',
  },
  {
    title: '练习提分',
    desc: '完成模拟考试，智能批改，精准分析薄弱点，针对性提升。',
  },
]
</script>

<style scoped>
.home-page {
  background: var(--bg-primary);
  min-height: 100vh;
}

/* ── Hero ─────────────────────────────────────── */
.hero {
  padding-top: calc(var(--nav-height) + 60px);
  padding-bottom: 80px;
}

.hero-inner {
  display: flex;
  align-items: center;
  gap: 64px;
}

.hero-left {
  flex: 1;
  min-width: 0;
}

.hero-right {
  flex-shrink: 0;
}

.hero-title {
  font-size: 60px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--color-primary);
  margin-bottom: 16px;
  letter-spacing: -2px;
  font-family: 'Noto Sans SC', var(--font-sans);
}

.hero-title-accent {
  color: var(--color-primary);
  display: block;
}

.hero-desc {
  font-size: 14px;
  color: var(--text-muted);
  line-height: 1.7;
  margin-bottom: 28px;
  max-width: 380px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 36px;
}

.login-hint {
  font-size: 14px;
  color: var(--text-muted);
}

.login-link {
  color: var(--color-primary);
  font-weight: 500;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-lbl {
  font-size: 13px;
  color: var(--text-muted);
}

.stat-sep {
  color: var(--border-color);
  font-size: 18px;
}

/* ── Features ─────────────────────────────────── */
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.feature-card {
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feat-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.feat-desc {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.6;
}

/* ── How It Works ─────────────────────────────── */
.steps-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 40px;
  position: relative;
}

.steps-row::before {
  content: '';
  position: absolute;
  top: 28px;
  left: calc(16.66% + 20px);
  right: calc(16.66% + 20px);
  height: 1px;
  background: var(--border-color);
  z-index: 0;
}

.step-item {
  position: relative;
  z-index: 1;
}

.step-num {
  font-size: 36px;
  font-weight: 800;
  color: var(--color-accent-light);
  line-height: 1;
  margin-bottom: 16px;
  font-variant-numeric: tabular-nums;
}

.step-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.step-desc {
  font-size: 14px;
  color: var(--text-muted);
  line-height: 1.6;
}

/* ── CTA ──────────────────────────────────────── */
.cta-card {
  background: var(--color-primary);
  border-radius: var(--radius-xl);
  padding: 60px 40px;
  text-align: center;
  color: white;
}

.cta-title {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 10px;
  color: white;
}

.cta-desc {
  font-size: 15px;
  opacity: 0.75;
  margin-bottom: 28px;
}

.cta-btn {
  background: white !important;
  color: var(--color-primary) !important;
  font-size: 15px;
  padding: 13px 32px;
  text-decoration: none;
  display: inline-block;
  border-radius: var(--radius-full);
  font-weight: 600;
  transition: all 0.2s;
}

.cta-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.2);
}

/* ── Footer ───────────────────────────────────── */
.footer {
  border-top: 1px solid var(--border-light);
  padding: 24px 0;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 6px;
}

.footer-copy {
  font-size: 13px;
  color: var(--text-muted);
}

/* ── Responsive ───────────────────────────────── */
@media (max-width: 1024px) {
  .features-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .hero-inner {
    flex-direction: column;
    gap: 40px;
    text-align: center;
  }
  .hero-title { font-size: 44px; }
  .hero-right { display: flex; justify-content: center; }
  .hero-actions { justify-content: center; }
  .hero-stats { justify-content: center; }
  .hero-desc { margin: 0 auto 28px; }
  .features-grid { grid-template-columns: 1fr; }
  .steps-row { grid-template-columns: 1fr; gap: 24px; }
  .steps-row::before { display: none; }
  .footer-inner { flex-direction: column; gap: 12px; text-align: center; }
}
</style>
