export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: {
    id: string;
    username: string;
    englishLevel: string;
  };
};

export type Scenario = {
  id: string;
  category: string;
  title: string;
  titleCn?: string;
  description: string;
  suggestedPrompts?: string[];
  difficulty: string;
};

export type PracticeSession = {
  id: string;
  scenarioId: string;
  status: string;
  difficulty: string;
};

export type TtsResponse = {
  text: string;
  voice: string;
  format: string;
  mimeType: string;
  durationMs: number;
  audioContentBase64: string;
};

export type VoiceDialogueResponse = {
  sessionId: string;
  turnIndex: number;
  asr: {
    text: string;
    confidence: number;
    durationMs: number;
    finalResult: boolean;
  };
  aiText: string;
  tts: TtsResponse;
  pronunciation: {
    pronunciationScore: number;
    fluencyScore: number;
    paceScore: number;
    overallScore: number;
    wordCount: number;
    wordsPerMinute: number;
    suggestions: string[];
    issues: Array<{ type: string; target: string; message: string }>;
  };
  correction: {
    correctedText: string;
    grammarScore: number;
    expressionScore: number;
    overallFeedback: string;
    corrections: Array<{
      type: string;
      original: string;
      corrected: string;
      explanation: string;
      severity: string;
    }>;
    betterExpressions: string[];
  };
};

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  message: string;
  timestamp: string;
};

export type SessionReport = {
  overallScore: number;
  pronunciationScore: number;
  fluencyScore: number;
  grammarScore: number;
  vocabularyScore: number;
  comprehensionScore: number;
  totalTurns: number;
  totalWords: number;
  strengths: string;
  weaknesses: string;
  teacherComment: string;
};
