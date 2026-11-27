import type { Questionnaire } from '../api/demoApi'
import type { Answers } from './demo'

export function showsQuestion(questionnaire: Questionnaire, answers: Answers, id: string) {
  return questionnaire.version !== 'v0.1.0' || id !== 'Q4' || answers.Q1?.includes('A') === true
}

export function submission(questionnaire: Questionnaire, answers: Answers) {
  return {
    questionnaireVersion: questionnaire.version,
    answers: questionnaire.questions.map(({ id, options }) => ({
      questionId: id,
      optionIds: showsQuestion(questionnaire, answers, id)
        ? options.filter(option => answers[id]?.includes(option.id)).map(option => option.id)
        : [],
    })),
  }
}

export const exclusionLimit = 100
