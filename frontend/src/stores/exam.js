import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/api'

const TASK1_KEYWORDS = [
  'task 1',
  'summarise the information',
  'summarize the information',
  'report the main features',
  'make comparisons where relevant',
  'the chart below',
  'the graph below',
  'the table below',
  'the diagram below',
  'the map below',
  'the process below',
  'line graph',
  'bar chart',
  'pie chart',
  'table below',
]

const TASK2_KEYWORDS = [
  'task 2',
  'write an essay',
  'to what extent do you agree or disagree',
  'discuss both views',
  'give your own opinion',
  'advantages outweigh the disadvantages',
  'do the advantages outweigh',
  'what are the causes',
  'what problems does this cause',
  'what solutions',
  'problem and solution',
  'positive or negative development',
]

const WRITING_SIGNALS = [
  'writing task',
  ...TASK1_KEYWORDS,
  ...TASK2_KEYWORDS,
]

function normalizeText(text) {
  return (text || '').toString().toLowerCase().replace(/\s+/g, ' ').trim()
}

function includesAny(text, keywords) {
  return keywords.some(keyword => text.includes(keyword))
}

function detectWritingTaskMeta(text, rawOptions = {}) {
  const normalized = normalizeText(text)
  const options = typeof rawOptions === 'string'
    ? (() => {
        try { return JSON.parse(rawOptions) } catch { return {} }
      })()
    : (rawOptions || {})

  const explicitTaskType = options.taskType === 'Task1' || options.taskType === 'Task2'
    ? options.taskType
    : null
  const explicitWordLimit = Number(options.wordLimit)
  const isLikelyWriting = includesAny(normalized, WRITING_SIGNALS) || explicitTaskType !== null

  let taskType = explicitTaskType
  if (!taskType) {
    if (includesAny(normalized, TASK1_KEYWORDS)) taskType = 'Task1'
    else if (includesAny(normalized, TASK2_KEYWORDS)) taskType = 'Task2'
    else if (normalized.includes('150 words') || normalized.includes('at least 150 words')) taskType = 'Task1'
    else if (normalized.includes('250 words') || normalized.includes('at least 250 words')) taskType = 'Task2'
    else if (normalized.includes('[visual data summary]') || normalized.includes('[table data]')) taskType = 'Task1'
  }

  let wordLimit = Number.isFinite(explicitWordLimit) && explicitWordLimit > 0 ? explicitWordLimit : undefined
  if (!wordLimit) {
    if (taskType === 'Task1') wordLimit = 150
    else if (taskType === 'Task2') wordLimit = 250
    else if (normalized.includes('150 words')) wordLimit = 150
    else if (normalized.includes('250 words')) wordLimit = 250
  }

  return {
    isWriting: isLikelyWriting,
    taskType: taskType || (wordLimit === 150 ? 'Task1' : 'Task2'),
    wordLimit: wordLimit || 250,
  }
}

const MOCK_EXAMS = [
  {
    id: 1,
    isMock: true,
    title: 'Cambridge IELTS 17 - Reading Passage 1',
    description: 'Academic Reading - The development of the London underground railway',
    type: 'reading',
    status: 'ready',
    questionCount: 13,
    duration: 20,
    difficulty: '中等',
    createdAt: '2024-03-01',
    tags: ['Academic', 'Reading'],
    sections: [
      {
        id: 's1',
        title: 'Passage 1: The development of the London underground railway',
        passage: `The development of the London underground railway

In the first half of the 1800s, London’s population grew at an astonishing rate, and the central area became increasingly congested. In addition, the expansion of the overground railway network resulted in more and more passengers arriving in the capital. However, in 1846, a Royal Commission decided that the railways should not be allowed to enter the City, the capital’s historic and business centre. The result was that the overground railway stations formed a ring around the City. The area within consisted of poorly built, overcrowded slums and the streets were full of horse-drawn traffic. Crossing the City became a nightmare. It could take an hour and a half to travel 8 km by horse-drawn carriage or bus. Numerous schemes were proposed to resolve these problems, but few succeeded.

Amongst the most vocal advocates for a solution to London’s traffic problems was Charles Pearson, who worked as a solicitor for the City of London. He saw both social and economic advantages in building an underground railway that would link the overground railway stations together and clear London slums at the same time. His idea was to relocate the poor workers who lived in the inner-city slums to newly constructed suburbs, and to provide cheap rail travel for them to get to work. Pearson’s ideas gained support amongst some businessmen and in 1851 he submitted a plan to Parliament. It was rejected, but coincided with a proposal from another group for an underground connecting line, which Parliament passed.

The two groups merged and established the Metropolitan Railway Company in August 1854. The company’s plan was to construct an underground railway line from the Great Western Railway’s (GWR) station at Paddington to the edge of the City at Farringdon Street – a distance of almost 5 km. The organisation had difficulty in raising the funding for such a radical and expensive scheme, not least because of the critical articles printed by the press. Objectors argued that the tunnels would collapse under the weight of traffic overhead, buildings would be shaken and passengers would be poisoned by the emissions from the train engines. However, Pearson and his partners persisted.

The GWR, aware that the new line would finally enable them to run trains into the heart of the City, invested almost £250,000 in the scheme. Eventually, over a five-year period, £1m was raised. The chosen route ran beneath existing main roads to minimise the expense of demolishing buildings. Originally scheduled to be completed in 21 months, the construction of the underground line took three years. It was built just below street level using a technique known as ‘cut and cover’. A trench about ten metres wide and six metres deep was dug, and the sides temporarily held up with timber beams. Brick walls were then constructed, and finally a brick arch was added to create a tunnel. A two-metre-deep layer of soil was laid on top of the tunnel and the road above rebuilt.

The Metropolitan line, which opened on 10 January 1863, was the world’s first underground railway. On its first day, almost 40,000 passengers were carried between Paddington and Farringdon, the journey taking about 18 minutes. By the end of the Metropolitan’s first year of operation, 9.5 million journeys had been made.

Even as the Metropolitan began operation, the first extensions to the line were being authorised; these were built over the next five years, reaching Moorgate in the east of London and Hammersmith in the west. The original plan was to pull the trains with steam locomotives, using firebricks in the boilers to provide steam, but the engines were never introduced. Instead, the line used specially designed locomotives that were fitted with water tanks in which steam could be condensed. However, smoke and fumes remained a problem, even though ventilation shafts were added to the tunnels.

Despite the extension of the underground railway, by the 1880s, congestion on London’s streets had become worse. The problem was partly that the existing underground lines formed a circuit around the centre of London and extended to the suburbs, but did not cross the capital’s centre. The ‘cut and cover’ method of construction was not an option in this part of the capital. The only alternative was to tunnel deep underground.

Although the technology to create these tunnels existed, steam locomotives could not be used in such a confined space. It wasn’t until the development of a reliable electric motor, and a means of transferring power from the generator to a moving train, that the world’s first deep-level electric railway, the City & South London, became possible. The line opened in 1890, and ran from the City to Stockwell, south of the River Thames. The trains were made up of three carriages and driven by electric engines. The carriages were narrow and had tiny windows just below the roof because it was thought that passengers would not want to look out at the tunnel walls. The line was not without its problems, mainly caused by an unreliable power supply. Although the City & South London Railway was a great technical achievement, it did not make a profit. Then, in 1900, the Central London Railway, known as the ‘Tuppenny Tube’, began operation using new electric locomotives. It was very popular and soon afterwards new railways and extensions were added to the growing tube network. By 1907, the heart of today’s Underground system was in place.`,
        questions: [
          {
            id: 'q1',
            type: 'fill',
            questionNumber: 1,
            text: 'The ________ of London increased rapidly between 1800 and 1850.',
            answer: 'population',
            explanation: '',
            locatorText: "London’s population grew at an astonishing rate",
          },
          {
            id: 'q2',
            type: 'fill',
            questionNumber: 2,
            text: 'Building the railway would make it possible to move people to better housing in the ________.',
            answer: 'suburbs',
            explanation: '',
            locatorText: 'newly constructed suburbs',
          },
          {
            id: 'q3',
            type: 'fill',
            questionNumber: 3,
            text: 'A number of ________ agreed with Pearson’s idea.',
            answer: 'businessmen',
            explanation: '',
            locatorText: 'support amongst some businessmen',
          },
          {
            id: 'q4',
            type: 'fill',
            questionNumber: 4,
            text: 'The company initially had problems getting the ________ needed for the project.',
            answer: 'funding',
            explanation: '',
            locatorText: 'difficulty in raising the funding',
          },
          {
            id: 'q5',
            type: 'fill',
            questionNumber: 5,
            text: 'Negative articles about the project appeared in the ________.',
            answer: 'press',
            explanation: '',
            locatorText: 'critical articles printed by the press',
          },
          {
            id: 'q6',
            type: 'fill',
            questionNumber: 6,
            text: 'With the completion of the brick arch, the tunnel was covered with ________.',
            answer: 'soil',
            explanation: '',
            locatorText: 'A two-metre-deep layer of soil was laid on top of the tunnel',
          },
          {
            id: 'q7',
            type: 'tfng',
            questionNumber: 7,
            text: 'Other countries had built underground railways before the Metropolitan line opened.',
            answer: 'FALSE',
            explanation: '',
            locatorText: "the world’s first underground railway",
          },
          {
            id: 'q8',
            type: 'tfng',
            questionNumber: 8,
            text: 'More people than predicted travelled on the Metropolitan line on the first day.',
            answer: 'NOT GIVEN',
            explanation: '',
            locatorText: 'almost 40,000 passengers were carried',
          },
          {
            id: 'q9',
            type: 'tfng',
            questionNumber: 9,
            text: 'The use of ventilation shafts failed to prevent pollution in the tunnels.',
            answer: 'TRUE',
            explanation: '',
            locatorText: 'smoke and fumes remained a problem, even though ventilation shafts were added',
          },
          {
            id: 'q10',
            type: 'tfng',
            questionNumber: 10,
            text: 'A different approach from the ‘cut and cover’ technique was required in London’s central area.',
            answer: 'TRUE',
            explanation: '',
            locatorText: "The ‘cut and cover’ method of construction was not an option in this part of the capital",
          },
          {
            id: 'q11',
            type: 'tfng',
            questionNumber: 11,
            text: 'The windows on City & South London trains were at eye level.',
            answer: 'FALSE',
            explanation: '',
            locatorText: 'tiny windows just below the roof',
          },
          {
            id: 'q12',
            type: 'tfng',
            questionNumber: 12,
            text: 'The City & South London Railway was a financial success.',
            answer: 'FALSE',
            explanation: '',
            locatorText: 'it did not make a profit',
          },
          {
            id: 'q13',
            type: 'tfng',
            questionNumber: 13,
            text: 'Trains on the ‘Tuppenny Tube’ nearly always ran on time.',
            answer: 'NOT GIVEN',
            explanation: '',
            locatorText: "known as the ‘Tuppenny Tube’, began operation",
          },
        ],
      },
    ],
  },
  {
    id: 2,
    isMock: true,
    title: 'Cambridge IELTS 17 - Writing Task 2',
    description: 'Academic Writing - Task 2',
    type: 'writing',
    status: 'ready',
    questionCount: 1,
    duration: 40,
    difficulty: '中等',
    createdAt: '2024-03-01',
    tags: ['Academic', 'Writing', 'Task 2'],
    sections: [
      {
        id: 'w2',
        title: 'Writing Task 2',
        passage: `WRITING TASK 2

You should spend about 40 minutes on this task.

Write about the following topic:

It is important for people to take risks, both in their professional lives and their personal lives.

Do you think the advantages of taking risks outweigh the disadvantages?

Give reasons for your answer and include any relevant examples from your own knowledge or experience.

Write at least 250 words.`,
        questions: [
          {
            id: 'w2q1',
            type: 'write',
            questionNumber: 1,
            text: `You should spend about 40 minutes on this task.

Write about the following topic:

It is important for people to take risks, both in their professional lives and their personal lives.

Do you think the advantages of taking risks outweigh the disadvantages?

Give reasons for your answer and include any relevant examples from your own knowledge or experience.

Write at least 250 words.`,
            answer: '思路要点：明确立场（advantages outweigh disadvantages 或反之），给出 2 个主因 + 1 个让步段（承认风险但可控）。举例可从职业跳槽/创业、个人学习新技能/搬家等场景选择，保证例子具体可解释。',
            explanation: `写作思路提示（Task 2 - Opinion, 40min）

1) 立场选择
- 开头明确回答题目：advantages outweigh disadvantages（或反之）。

2) 段落结构（推荐 4 段）
- Introduction：改写题目 + 表明立场。
- Body 1：优势 1（例如：促进成长/突破舒适区/抓住机会）+ 具体例子。
- Body 2：优势 2（例如：职业回报/学习效率/抗压能力）+ 具体例子。
- Body 3（让步段）：承认风险（财务/时间/心理压力），但说明如何降低（评估、备选方案、小步试错、信息收集）。
- Conclusion：重申立场 + 总结两点理由。

3) 论证要点
- 避免空泛：每个理由至少包含“原因 -> 结果 -> 例子”。
- 风险要“可控”：强调 calculated risks / informed decisions。

4) 常用表达
- take calculated risks; step out of one’s comfort zone; potential payoff; mitigate risks; contingency plan.

5) 常见扣分点
- 只列举优缺点但不表态；
- 例子太泛或与观点无关；
- 结论引入新观点。`,
            locatorText: 'advantages of taking risks outweigh the disadvantages',
            options: {
              taskType: 'Task2',
              wordLimit: 250,
            },
          },
        ],
      },
    ],
  },
]

export const useExamStore = defineStore('exam', () => {
  const exams = ref([...MOCK_EXAMS])
  const currentExam = ref(null)
  const currentAnswers = ref({})
  const examStartTime = ref(null)
  const examResult = ref(null)
  const isSubmitting = ref(false)
  const examHistory = ref(JSON.parse(localStorage.getItem('ielts_exam_history') || '[]'))

  const currentExamQuestions = computed(() => {
    if (!currentExam.value) return []
    return currentExam.value.sections?.flatMap(s => s.questions) || []
  })

  const answeredCount = computed(() => Object.keys(currentAnswers.value).length)
  const totalQuestions = computed(() => currentExamQuestions.value.length)

  function loadExam(id) {
    const exam = exams.value.find(e => e.id === Number(id))
    if (exam) {
      currentExam.value = { ...exam }
      currentAnswers.value = {}
      examStartTime.value = Date.now()
      return true
    }
    return false
  }

  function setAnswer(questionId, answer) {
    currentAnswers.value = { ...currentAnswers.value, [questionId]: answer }
  }

  function getAnswer(questionId) {
    return currentAnswers.value[questionId] ?? ''
  }

  async function submitExam() {
    isSubmitting.value = true
    const questions = currentExamQuestions.value
    const timeUsed = Math.round((Date.now() - examStartTime.value) / 1000)
    const token = localStorage.getItem('ielts_token') || ''
    const isMock = !token || token.startsWith('mock_token_')

    let results, correct, total, band, serverRecordId = null, serverExamTitle = null

    if (!isMock && !currentExam.value.isMock && currentExam.value.id && typeof currentExam.value.id === 'number') {
      try {
        const answersPayload = {}
        for (const [qId, ans] of Object.entries(currentAnswers.value)) {
          answersPayload[qId] = ans
        }
        const res = await request.post('/exams/submit', {
          examId: currentExam.value.id,
          answers: answersPayload,
          timeUsed,
        })
        const serverData = res.data
        correct = serverData.correct
        total = serverData.total
        band = serverData.band
        // Map server questions; merge local question metadata for write type
        const localQMap = {}
        questions.forEach(q => { localQMap[q.id] = q })
        results = (serverData.questions || []).map(q => {
          const local = localQMap[String(q.id)] || {}
          const mapped = {
            id: String(q.id),
            type: q.type,
            questionNumber: q.questionNumber,
            text: q.questionText || q.text || '',
            answer: q.answer || '',
            userAnswer: q.userAnswer || '',
            isCorrect: !!q.isCorrect,
            isWrite: !!q.isWrite,
            explanation: q.explanation || '',
            locatorText: q.locatorText || '',
          }
          if (q.isWrite) {
            mapped.taskType = local.taskType || 'Task2'
            mapped.wordLimit = local.wordLimit || 250
          }
          return mapped
        })
        serverRecordId = serverData.recordId || null
        serverExamTitle = serverData.examTitle || null
      } catch {
        // Backend unavailable – fall back to local scoring
        results = _localScore(questions)
        const gradable2 = results.filter(r => !r.isWrite)
        correct = gradable2.filter(r => r.isCorrect).length
        total = gradable2.length
        band = getBandScore(correct, total || 1)
      }
    } else {
      results = _localScore(questions)
      const gradable = results.filter(r => !r.isWrite)
      correct = gradable.filter(r => r.isCorrect).length
      total = gradable.length
      band = getBandScore(correct, total || 1)
    }

    examResult.value = {
      examId: currentExam.value.id,
      examTitle: currentExam.value.title,
      questions: results,
      correct,
      total,
      score: Math.round((correct / (total || 1)) * 40),
      band,
      timeUsed,
      submittedAt: new Date().toISOString(),
    }

    const historyEntry = {
      recordId: serverRecordId,
      examId: examResult.value.examId,
      examTitle: serverExamTitle || examResult.value.examTitle,
      band: examResult.value.band,
      correct: examResult.value.correct,
      total: examResult.value.total,
      timeUsed: examResult.value.timeUsed,
      submittedAt: examResult.value.submittedAt,
    }
    examHistory.value.unshift(historyEntry)
    examHistory.value = examHistory.value.slice(0, 50)
    localStorage.setItem('ielts_exam_history', JSON.stringify(examHistory.value))

    const errorBook = JSON.parse(localStorage.getItem('ielts_error_book') || '[]')
    const wrongOnes = results.filter(r => !r.isCorrect).map(r => ({
      id: `${currentExam.value.id}_${r.id}`,
      examId: currentExam.value.id,
      examTitle: currentExam.value.title,
      type: r.type,
      text: r.text,
      userAnswer: r.userAnswer,
      answer: r.answer,
      explanation: r.explanation,
      locatorText: r.locatorText,
      addedAt: new Date().toISOString(),
    }))
    const mergedErrors = [...wrongOnes, ...errorBook.filter(e => !wrongOnes.find(w => w.id === e.id))]
    localStorage.setItem('ielts_error_book', JSON.stringify(mergedErrors.slice(0, 200)))

    // AI grading for write-type questions
    const writeResults = results.filter(r => r.isWrite)
    if (writeResults.length > 0) {
      try {
        await Promise.all(writeResults.map(async (r) => {
          if (!r.userAnswer) return
          try {
            const res = await request.post('/exams/grade-writing', {
              taskPrompt: r.text,
              userEssay: r.userAnswer,
              wordLimit: r.wordLimit || 250,
            })
            r.aiGrade = res.data
          } catch { /* grading failed silently */ }
        }))
        // Recalculate band from AI grades for writing exams
        const graded = writeResults.filter(r => r.aiGrade?.band)
        if (graded.length > 0) {
          const avgBand = graded.reduce((s, r) => s + Number(r.aiGrade.band), 0) / graded.length
          examResult.value.band = Math.round(avgBand * 2) / 2 // round to nearest 0.5
        }
      } catch { /* ignore */ }
    }

    // Persist full result (including AI grades) so it survives page refresh
    try {
      localStorage.setItem('ielts_last_result', JSON.stringify(examResult.value))
    } catch { /* ignore quota errors */ }

    // Save AI feedback to DB so it persists across devices/sessions
    if (serverRecordId && writeResults.some(r => r.aiGrade)) {
      try {
        const feedbackByQId = {}
        writeResults.forEach(r => { if (r.aiGrade) feedbackByQId[r.id] = r.aiGrade })
        await request.patch(`/exams/records/${serverRecordId}/ai-feedback`, {
          feedback: feedbackByQId,
          band: examResult.value.band,
        })
      } catch { /* non-critical, localStorage fallback still works */ }
    }

    isSubmitting.value = false
    return examResult.value
  }

  function _localScore(questions) {
    return questions.map(q => {
      if (q.type === 'write') {
        return { ...q, userAnswer: currentAnswers.value[q.id] || '', isCorrect: false, isWrite: true }
      }
      const userAnswer = (currentAnswers.value[q.id] || '').toString().trim().toUpperCase()
      const correct = q.answer.toString().trim().toUpperCase()
      return { ...q, userAnswer: currentAnswers.value[q.id] || '', isCorrect: userAnswer === correct }
    })
  }

  function getBandScore(correct, total) {
    const ratio = correct / total
    if (ratio >= 0.925) return 9.0
    if (ratio >= 0.875) return 8.5
    if (ratio >= 0.825) return 8.0
    if (ratio >= 0.775) return 7.5
    if (ratio >= 0.700) return 7.0
    if (ratio >= 0.625) return 6.5
    if (ratio >= 0.550) return 6.0
    if (ratio >= 0.475) return 5.5
    if (ratio >= 0.400) return 5.0
    if (ratio >= 0.325) return 4.5
    return 4.0
  }

  function addExam(exam) {
    const newExam = {
      ...exam,
      id: Date.now(),
      status: 'processing',
      createdAt: new Date().toISOString().split('T')[0],
      sections: [],
    }
    exams.value.unshift(newExam)
    setTimeout(() => {
      const idx = exams.value.findIndex(e => e.id === newExam.id)
      if (idx !== -1) {
        exams.value[idx] = { ...exams.value[idx], status: 'ready' }
      }
    }, 3000)
    return newExam
  }

  function addPendingExam(id, title, type, duration = 60) {
    exams.value.unshift({
      id,
      title,
      description: `上传于 ${new Date().toLocaleDateString()}`,
      type,
      status: 'processing',
      questionCount: 0,
      duration,
      difficulty: '中等',
      createdAt: new Date().toISOString().split('T')[0],
      tags: [],
      sections: [],
    })
  }

  function updateParsedExam(id, examData, questions) {
    const idx = exams.value.findIndex(e => e.id === id)
    if (idx === -1) return
    let passage = ''
    let generatedWritingExamType = null
    try {
      const parsed = examData.parseResult ? JSON.parse(examData.parseResult) : {}
      passage = parsed.passages ? parsed.passages.join('\n\n') : ''
      
      // Auto-generate writing task question if questions is empty but passage contains writing task
      if ((!questions || questions.length === 0) && passage) {
        const detected = detectWritingTaskMeta(passage)
        if (detected.isWriting) {
          console.log('[updateParsedExam] Auto-generating writing task question from passage')
          generatedWritingExamType = 'writing'
          questions = [{
            id: `auto-write-${id}`,
            type: 'write',
            questionNumber: 1,
            text: passage,
            answer: 'Approach: Identify main trends, compare data across regions, and highlight significant features.',
            explanation: 'Task Achievement: Report main features and comparisons. Coherence: Organize logically. Vocabulary: Use precise terms. Grammar: Use varied structures.',
            locatorText: detected.taskType === 'Task1' ? 'summarise the information' : 'write an essay',
            options: JSON.stringify({ taskType: detected.taskType, wordLimit: detected.wordLimit })
          }]
        }
      }
    } catch { /* ignore */ }
    const section = {
      id: `s${id}`,
      title: examData.title,
      passage,
      questions: questions.map(q => {
        let options
        if (q.options) {
          try {
            const raw = typeof q.options === 'string' ? JSON.parse(q.options) : q.options
            options = Array.isArray(raw)
              ? raw
              : Object.entries(raw).map(([label, text]) => ({ label, text: String(text) }))
          } catch { options = undefined }
        }
        let taskType, wordLimit
        let type = q.type || 'fill'
        
        // Auto-detect writing task from text if type is not 'write'
        const text = q.questionText || q.text || ''
        const detected = detectWritingTaskMeta(text, q.options)
        if (type !== 'write' && type !== 'mcq' && type !== 'tfng') {
          if (detected.isWriting) {
            type = 'write'
            console.log('[updateParsedExam] Auto-detected writing task for question', q.id)
          }
        }
        
        if (type === 'write') {
          taskType = detected.taskType
          wordLimit = detected.wordLimit
          options = undefined
        }
        return {
          id: String(q.id),
          type,
          questionNumber: q.questionNumber,
          text,
          answer: q.answer || '',
          explanation: q.explanation || '请参考官方答案',
          locatorText: q.locatorText || '',
          options,
          taskType,
          wordLimit,
        }
      })
    }
    const hasWrite = section.questions.some(q => q.type === 'write')
    exams.value[idx] = {
      ...exams.value[idx],
      status: 'ready',
      questionCount: questions.length,
      type: hasWrite ? (generatedWritingExamType || 'writing') : (exams.value[idx].type || 'reading'),
      sections: [section],
    }
  }

  async function deleteExam(id) {
    const exam = exams.value.find(e => e.id === id)
    if (!exam) return
    // For real backend exams, call the API first
    if (!exam.isMock) {
      try {
        await request.delete(`/exams/${id}`)
      } catch (e) {
        // If backend unavailable, still remove from local list
      }
    }
    exams.value = exams.value.filter(e => e.id !== id)
    // Remove associated exam history records
    examHistory.value = examHistory.value.filter(h => h.examId !== id)
    localStorage.setItem('ielts_exam_history', JSON.stringify(examHistory.value))
    // Remove associated error book entries
    const errorBook = JSON.parse(localStorage.getItem('ielts_error_book') || '[]')
    const filteredErrors = errorBook.filter(e => e.examId !== id)
    localStorage.setItem('ielts_error_book', JSON.stringify(filteredErrors))
  }

  function markExamError(id) {
    const idx = exams.value.findIndex(e => e.id === id)
    if (idx !== -1) exams.value[idx] = { ...exams.value[idx], status: 'error' }
  }

  async function loadExams() {
    const token = localStorage.getItem('ielts_token') || ''
    if (!token || token.startsWith('mock_token_')) return
    try {
      const res = await request.get('/exams')
      const serverExams = res.data || []
      // Build real exam list
      const mapped = serverExams.map(e => ({
        id: e.id,
        title: e.title,
        description: e.description || '',
        type: e.type || 'reading',
        status: e.status,
        questionCount: e.questionCount || 0,
        duration: e.duration || 60,
        difficulty: e.difficulty || '中等',
        createdAt: e.createdAt ? e.createdAt.substring(0, 10) : new Date().toISOString().split('T')[0],
        tags: [{ reading: '阅读', listening: '听力', writing: '写作' }[e.type] || e.type],
        sections: [],
      }))
      // Merge: real DB exams first, then mock exams (always keep as starter content)
      const realIds = new Set(mapped.map(e => e.id))
      const mocks = MOCK_EXAMS.filter(e => !realIds.has(e.id))
      exams.value = [...mapped, ...mocks]
      // Load questions for ready real exams in parallel
      const readyExams = mapped.filter(e => e.status === 'ready')
      await Promise.all(readyExams.map(async (e) => {
        try {
          const [examRes, qRes] = await Promise.all([
            request.get(`/exams/${e.id}`),
            request.get(`/exams/${e.id}/questions`),
          ])
          updateParsedExam(e.id, examRes.data, qRes.data || [])
        } catch { /* ignore individual fetch failures */ }
      }))
    } catch { /* backend unreachable, keep mock data */ }
  }

  function getExamHistory() {
    return examHistory.value
  }

  async function loadHistory() {
    const token = localStorage.getItem('ielts_token') || ''
    if (!token || token.startsWith('mock_token_')) return
    try {
      const res = await request.get('/exams/history')
      const records = res.data || []
      if (records.length > 0) {
        // Backend has real records — use them as source of truth
        examHistory.value = records.map(r => ({
          recordId: r.id,
          examId: r.examId,
          examTitle: r.examTitle || '模拟考试',
          examType: r.examType || 'reading',
          band: r.bandScore,
          correct: r.correctCount,
          total: r.totalCount,
          timeUsed: r.timeUsed,
          submittedAt: r.submittedAt,
        }))
        localStorage.setItem('ielts_exam_history', JSON.stringify(examHistory.value))
      }
      // If backend returns empty, keep existing localStorage history as fallback
    } catch { /* offline — keep localStorage fallback */ }
  }

  async function loadRecord(recordId) {
    const token = localStorage.getItem('ielts_token') || ''
    if (!token || token.startsWith('mock_token_')) return false
    try {
      const res = await request.get(`/exams/records/${recordId}`)
      const d = res.data
      const questions = currentExam.value?.sections?.flatMap(s => s.questions) || []
      const localQMap = {}
      questions.forEach(q => { localQMap[q.id] = q })
      const results = (d.questions || []).map(q => {
        const local = localQMap[String(q.id)] || {}
        const mapped = {
          id: String(q.id),
          type: q.type,
          questionNumber: q.questionNumber,
          text: q.questionText || q.text || '',
          answer: q.answer || '',
          userAnswer: q.userAnswer || '',
          isCorrect: !!q.isCorrect,
          isWrite: !!q.isWrite,
          explanation: q.explanation || '',
          locatorText: q.locatorText || '',
        }
        if (q.isWrite) {
          mapped.taskType = local.taskType || 'Task2'
          mapped.wordLimit = local.wordLimit || 250
          if (q.aiGrade) mapped.aiGrade = q.aiGrade
        }
        return mapped
      })
      examResult.value = {
        recordId: d.recordId,
        examId: d.examId,
        examTitle: d.examTitle || '历史记录',
        questions: results,
        correct: d.correct,
        total: d.total,
        band: d.band,
        timeUsed: d.timeUsed,
        submittedAt: d.submittedAt,
        score: Math.round((d.correct / (d.total || 1)) * 40),
      }
      return true
    } catch { return false }
  }

  return { exams, currentExam, currentAnswers, examResult, isSubmitting, examHistory, deleteExam, currentExamQuestions, answeredCount, totalQuestions,
    loadExam, setAnswer, getAnswer, submitExam, addExam, getExamHistory,
    loadHistory, loadRecord, loadExams,
    addPendingExam, updateParsedExam, markExamError,
  }
})
