import { createElement, useEffect, useRef, useState } from "react";
import { base64ToAudioUrl, encodeWavFromFloat32, parseJsonList } from "./audio";
import {
  createSession,
  generateReport,
  getCurrentUser,
  getReport,
  getSessionDetail,
  listScenarios,
  listSessions,
  login,
  register,
  sendVoiceDialogue,
  updateCurrentUser
} from "./api";
import type { ConversationTurnHistory, PracticeSession, Scenario, SessionDetail, SessionReport, UserProfile, VoiceDialogueResponse } from "./types";

type Turn = VoiceDialogueResponse & {
  audioUrl?: string;
};

type CorrectionItem = Turn["correction"]["corrections"][number];

type WebAudioWindow = Window & {
  webkitAudioContext?: typeof AudioContext;
};

type AuthMode = "login" | "register";

const HISTORY_CACHE_KEY = "smartVoiceHistoryCache";

export default function App() {
  const [token, setToken] = useState(localStorage.getItem("smartVoiceToken") ?? "");
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(() => readStoredUser());
  const [authMode, setAuthMode] = useState<AuthMode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [registerEmail, setRegisterEmail] = useState("");
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [selectedScenarioId, setSelectedScenarioId] = useState("");
  const [session, setSession] = useState<PracticeSession | null>(null);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [report, setReport] = useState<SessionReport | null>(null);
  const [isReportOpen, setIsReportOpen] = useState(false);
  const [historySessions, setHistorySessions] = useState<PracticeSession[]>([]);
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [profileEmail, setProfileEmail] = useState("");
  const [profileLevel, setProfileLevel] = useState("INTERMEDIATE");
  const [isRecording, setIsRecording] = useState(false);
  const [isBusy, setIsBusy] = useState(false);
  const [isProfileSaving, setIsProfileSaving] = useState(false);
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);
  const [isCreatingSession, setIsCreatingSession] = useState(false);
  const [selectedFeedbackTurnIndex, setSelectedFeedbackTurnIndex] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [recordStartedAt, setRecordStartedAt] = useState<number | null>(null);

  const audioContextRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Float32Array[]>([]);
  const conversationEndRef = useRef<HTMLDivElement | null>(null);

  const selectedScenario = scenarios.find((scenario) => scenario.id === selectedScenarioId);
  const scenarioForCreate = selectedScenario ?? scenarios[0];
  const activeScenario = session
    ? scenarios.find((scenario) => scenario.id === session.scenarioId) ?? selectedScenario
    : selectedScenario;
  const latestTurn = turns.length > 0 ? turns[turns.length - 1] : undefined;
  const feedbackTurn = turns.find((turn) => turn.turnIndex === selectedFeedbackTurnIndex) ?? latestTurn;
  const isSessionCompleted = session?.status === "COMPLETED";
  const canStartRecording = Boolean(session?.id) && !isBusy && !isCreatingSession && !isSessionCompleted;
  const visibleHistorySessions = historySessions;

  useEffect(() => {
    if (!token) return;
    void loadScenarioOptions();
  }, [token]);

  useEffect(() => {
    if (!token) return;
    void refreshAccountData();
  }, [token]);

  useEffect(() => {
    conversationEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [turns.length, isBusy]);

  function applyAuthSession(auth: Awaited<ReturnType<typeof login>>) {
    localStorage.setItem("smartVoiceToken", auth.accessToken);
    localStorage.setItem("smartVoiceUser", JSON.stringify(auth.user));
    setToken(auth.accessToken);
    setCurrentUser(auth.user);
    setProfileEmail(auth.user.email ?? "");
    setProfileLevel(auth.user.englishLevel ?? "INTERMEDIATE");
  }

  async function handleLogin() {
    setError("");
    if (!username.trim() || !password.trim()) {
      setError("请输入用户名和密码。");
      return;
    }
    setIsBusy(true);
    try {
      const auth = await login(username.trim(), password);
      applyAuthSession(auth);
    } catch {
      setError("用户名或密码错误");
    } finally {
      setIsBusy(false);
    }
  }

  async function handleRegister() {
    setError("");
    if (!username.trim() || !password.trim()) {
      setError("请输入用户名和密码。");
      return;
    }
    if (password.length < 6) {
      setError("密码至少需要 6 位。");
      return;
    }
    setIsBusy(true);
    try {
      const auth = await register(username.trim(), password, registerEmail.trim() || undefined);
      applyAuthSession(auth);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Register failed");
    } finally {
      setIsBusy(false);
    }
  }

  function handleLogout() {
    localStorage.removeItem("smartVoiceToken");
    localStorage.removeItem("smartVoiceUser");
    setToken("");
    setCurrentUser(null);
    setSession(null);
    setTurns([]);
    setSelectedFeedbackTurnIndex(null);
    setReport(null);
    setIsReportOpen(false);
    setHistorySessions([]);
    setIsAccountMenuOpen(false);
    setIsProfileOpen(false);
    setError("");
  }

  function handleViewProfile() {
    setIsAccountMenuOpen(false);
    setIsProfileOpen(true);
  }

  async function refreshAccountData(sessionToKeep?: PracticeSession) {
    try {
      const [profile, history] = await Promise.all([
        getCurrentUser(token),
        listSessions(token)
      ]);
      setCurrentUser(profile);
      localStorage.setItem("smartVoiceUser", JSON.stringify(profile));
      setProfileEmail(profile.email ?? "");
      setProfileLevel(profile.englishLevel ?? "INTERMEDIATE");
      setHistorySessions(sessionToKeep ? upsertHistorySession(history.records, sessionToKeep) : history.records);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load account data");
    }
  }

  async function loadScenarioOptions() {
    setError("");
    try {
      const data = await listScenarios(token);
      setScenarios(data);
      setSelectedScenarioId((current) => {
        if (current && data.some((scenario) => scenario.id === current)) {
          return current;
        }
        return data[0]?.id || "";
      });
    } catch (err) {
      setScenarios([]);
      setSelectedScenarioId("");
      setError(err instanceof Error ? err.message : "Failed to load scenarios");
    }
  }

  async function handleSaveProfile() {
    setIsProfileSaving(true);
    setError("");
    try {
      const profile = await updateCurrentUser(token, {
        email: profileEmail,
        englishLevel: profileLevel
      });
      setCurrentUser(profile);
      localStorage.setItem("smartVoiceUser", JSON.stringify(profile));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update profile");
    } finally {
      setIsProfileSaving(false);
    }
  }

  function handleScenarioChange(nextScenarioId: string) {
    setSelectedScenarioId(nextScenarioId);
    setError("");
  }

  async function createSessionForSelectedScenario() {
    const scenario = scenarios.find((item) => item.id === selectedScenarioId) ?? scenarioForCreate;
    const scenarioId = scenario?.id;
    if (!scenarioId) {
      setError("暂无可用训练场景，请先初始化 scenarios 表数据。");
      return null;
    }
    setError("");
    setIsCreatingSession(true);
    try {
      const created = await createSession(token, scenarioId, scenario?.difficulty ?? "BEGINNER");
      const normalized = normalizePracticeSession(created, scenario);
      setReport(null);
      setIsReportOpen(false);
      setSession(normalized);
      setSelectedScenarioId(normalized.scenarioId);
      setTurns([]);
      setSelectedFeedbackTurnIndex(null);
      setHistorySessions((current) => upsertHistorySession(current, normalized));
      await refreshAccountData(normalized);
      return normalized;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create session");
      return null;
    } finally {
      setIsCreatingSession(false);
    }
  }

  async function handleCreateSession() {
    await createSessionForSelectedScenario();
  }

  async function startRecording() {
    if (!session?.id) {
      setError("请先选择场景并创建新对话。");
      return;
    }
    if (isSessionCompleted) {
      setError("本次会话已结课。想继续练习请先创建新对话。");
      return;
    }
    setError("");
    chunksRef.current = [];

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const AudioContextCtor = window.AudioContext || (window as WebAudioWindow).webkitAudioContext;
      if (!AudioContextCtor) throw new Error("This browser does not support Web Audio recording.");

      const audioContext = new AudioContextCtor();
      const source = audioContext.createMediaStreamSource(stream);
      const processor = audioContext.createScriptProcessor(4096, 1, 1);
      processor.onaudioprocess = (event) => {
        chunksRef.current.push(new Float32Array(event.inputBuffer.getChannelData(0)));
      };
      source.connect(processor);
      processor.connect(audioContext.destination);

      streamRef.current = stream;
      audioContextRef.current = audioContext;
      sourceRef.current = source;
      processorRef.current = processor;
      setRecordStartedAt(Date.now());
      setIsRecording(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start recording");
    }
  }

  function stopRecording() {
    processorRef.current?.disconnect();
    sourceRef.current?.disconnect();
    streamRef.current?.getTracks().forEach((track) => track.stop());

    const sampleRate = audioContextRef.current?.sampleRate ?? 48000;
    void audioContextRef.current?.close();

    processorRef.current = null;
    sourceRef.current = null;
    streamRef.current = null;
    audioContextRef.current = null;
    setIsRecording(false);

    if (chunksRef.current.length === 0) {
      setError("No audio was captured. Please check microphone permission and try again.");
      setRecordStartedAt(null);
      return;
    }

    const wav = encodeWavFromFloat32(chunksRef.current, sampleRate, 16000);
    void uploadRecording(wav);
  }

  async function uploadRecording(audio: Blob) {
    if (!session) return;
    setIsBusy(true);
    setError("");
    try {
      const durationMs = recordStartedAt ? Date.now() - recordStartedAt : undefined;
      const response = await sendVoiceDialogue({
        token,
        sessionId: session.id,
        audio,
        durationMs,
        voice: "zhixiaobai"
      });
      const audioUrl = response.tts?.audioContentBase64
        ? base64ToAudioUrl(response.tts.audioContentBase64, response.tts.mimeType)
        : undefined;
      setTurns((current) => {
        const nextTurns = [...current, { ...response, audioUrl }];
        cacheSessionDetail(session, nextTurns);
        return nextTurns;
      });
      setSelectedFeedbackTurnIndex(response.turnIndex);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Voice request failed");
    } finally {
      setIsBusy(false);
      setRecordStartedAt(null);
    }
  }

  async function handleGenerateReport() {
    if (!session) return;
    setIsBusy(true);
    setError("");
    try {
      const response = await generateReport(token, session.id);
      setReport(response.data);
      setIsReportOpen(true);
      setSession((current) => {
        const nextSession = current ? { ...current, status: "COMPLETED" } : current;
        if (nextSession) {
          cacheSessionDetail(nextSession, turns);
        }
        return nextSession;
      });
      await refreshAccountData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate report");
    } finally {
      setIsBusy(false);
    }
  }

  async function handleReportAction() {
    if (!session) return;
    if (!isSessionCompleted) {
      await handleGenerateReport();
      return;
    }
    if (report) {
      setIsReportOpen(true);
      return;
    }
    setIsBusy(true);
    setError("");
    try {
      const response = await getReport(token, session.id);
      setReport(response.data);
      setIsReportOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load report");
    } finally {
      setIsBusy(false);
    }
  }

  async function handleOpenHistory(item: PracticeSession) {
    setIsHistoryLoading(true);
    setError("");
    try {
      const detail = await loadSessionDetail(item);
      const restoredSession: PracticeSession = {
        id: detail.id,
        scenarioId: detail.scenarioId,
        status: detail.status,
        difficulty: detail.difficulty,
        startedAt: detail.startedAt,
        endedAt: detail.endedAt,
        durationSec: detail.durationSec
      };
      const restoredTurns = detail.turns.map((turn) => historyTurnToVoiceTurn(restoredSession.id, turn));
      setSession(restoredSession);
      setSelectedScenarioId(restoredSession.scenarioId);
      setTurns(restoredTurns);
      setSelectedFeedbackTurnIndex(restoredTurns[restoredTurns.length - 1]?.turnIndex ?? null);
      setReport(null);
      setIsReportOpen(false);
      setIsAccountMenuOpen(false);
      cacheSessionDetail(restoredSession, restoredTurns);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load history";
      setError(message.includes("Resource not found") || message.includes("No static resource") || message.includes("HTTP 404")
        ? "历史详情接口不可用，请重启后端后再查看历史对话。"
        : message);
    } finally {
      setIsHistoryLoading(false);
    }
  }

  async function loadSessionDetail(item: PracticeSession) {
    try {
      const detail = await getSessionDetail(token, item.id);
      const cachedDetail = readCachedSessionDetail(item.id);
      if (item.id === session?.id && turns.length > detail.turns.length) {
        return buildCurrentSessionDetail(item);
      }
      if (cachedDetail && cachedDetail.turns.length > detail.turns.length) {
        return { ...item, ...cachedDetail };
      }
      return detail;
    } catch (err) {
      if (item.id === session?.id && turns.length > 0) {
        return buildCurrentSessionDetail(item);
      }
      const cachedDetail = readCachedSessionDetail(item.id);
      if (cachedDetail?.turns.length) {
        return { ...item, ...cachedDetail };
      }
      throw err;
    }
  }

  function buildCurrentSessionDetail(item: PracticeSession): SessionDetail {
    return {
      ...item,
      turns: turns.map((turn) => ({
        id: `${turn.sessionId}-${turn.turnIndex}`,
        turnIndex: turn.turnIndex,
        userText: turn.asr.text || "本轮未识别到有效文本",
        aiText: turn.aiText,
        pronunciationScore: turn.pronunciation.pronunciationScore,
        fluencyScore: turn.pronunciation.fluencyScore
      }))
    };
  }

  if (!token) {
    const isRegisterMode = authMode === "register";

    return (
      <main className="login-page">
        <section className="login-copy">
          <p className="eyebrow">AI 英语口语陪练</p>
          <h1>{isRegisterMode ? "创建你的口语训练档案" : "进入你的场景口语训练"}</h1>
          <p>{isRegisterMode
            ? "注册后即可选择面试、点餐、会议等场景，开始积累自己的口语练习记录。"
            : "登录后选择面试、点餐、会议等场景，录音对话并查看发音、纠错和课后总结。"}</p>
        </section>

        <section className="login-panel">
          <div className="auth-tabs" role="tablist" aria-label="账号入口">
            <button
              className={authMode === "login" ? "active" : ""}
              onClick={() => {
                setAuthMode("login");
                setError("");
              }}
              type="button"
              role="tab"
              aria-selected={authMode === "login"}
            >
              登录
            </button>
            <button
              className={authMode === "register" ? "active" : ""}
              onClick={() => {
                setAuthMode("register");
                setError("");
              }}
              type="button"
              role="tab"
              aria-selected={authMode === "register"}
            >
              注册
            </button>
          </div>
          <h2>{isRegisterMode ? "注册账号" : "登录"}</h2>
          {error && <div className="error-strip">{error}</div>}
          <label>
            用户名
            <input value={username} onChange={(event) => setUsername(event.target.value)} />
          </label>
          {isRegisterMode && (
            <label>
              邮箱
              <input type="email" value={registerEmail} onChange={(event) => setRegisterEmail(event.target.value)} placeholder="可选" />
            </label>
          )}
          <label>
            密码
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          <button
            onClick={isRegisterMode ? handleRegister : handleLogin}
            disabled={isBusy || !username.trim() || !password.trim() || (isRegisterMode && password.length < 6)}
          >
            {isBusy ? (isRegisterMode ? "注册中..." : "登录中...") : (isRegisterMode ? "创建账号" : "进入训练")}
          </button>
          <p className="auth-note">
            {isRegisterMode ? "密码至少 6 位，注册成功后会直接进入训练页面。" : "还没有账号时，可切换到注册创建训练档案。"}
          </p>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <header className="page-header">
        <h1>AI口语练习</h1>
        <div className="account-menu account-menu-header">
          <button
            className="account-trigger"
            onClick={() => setIsAccountMenuOpen((current) => !current)}
            aria-haspopup="menu"
            aria-expanded={isAccountMenuOpen}
            aria-label="打开账户菜单"
            disabled={isBusy || isRecording || isProfileSaving || isHistoryLoading || isCreatingSession}
          >
            <span className="account-glyph" aria-hidden="true" />
          </button>
          {isAccountMenuOpen && (
            <div className="account-dropdown" role="menu">
              <button type="button" role="menuitem" onClick={handleViewProfile}>
                查看资料
              </button>
              <button type="button" role="menuitem" onClick={handleLogout}>
                退出登录
              </button>
            </div>
          )}
        </div>
      </header>
      <section className="app-workbench">
        <aside className="history-sidebar">
          <div className="new-chat-setup">
            <label>
              新对话场景
              <select value={selectedScenarioId} onChange={(event) => handleScenarioChange(event.target.value)} disabled={isCreatingSession || scenarios.length === 0}>
                {scenarios.map((scenario) => createElement("option", { key: scenario.id, value: scenario.id }, scenario.titleCn || scenario.title))}
              </select>
            </label>
            <button className="new-chat-button" onClick={handleCreateSession} disabled={isCreatingSession || scenarios.length === 0}>
              <span aria-hidden="true">+</span>
              {isCreatingSession ? "创建中..." : "新对话"}
            </button>
          </div>
          <div className="sidebar-heading">
            <span>对话</span>
            <button className="text-button" onClick={() => void refreshAccountData(session ?? undefined)} disabled={isHistoryLoading}>
              {isHistoryLoading ? "加载中" : "刷新"}
            </button>
          </div>
          <div className="history-list">
            {visibleHistorySessions.length ? (
              visibleHistorySessions.map((item) => {
                const scenario = scenarios.find((candidate) => candidate.id === item.scenarioId);
                const cachedDetail = readCachedSessionDetail(item.id);
                const cachedTurns = cachedDetail?.turns ?? [];
                const preview = cachedTurns[cachedTurns.length - 1]?.userText || (item.status === "COMPLETED" ? "已生成课后总结" : "继续这次练习");
                return createElement(
                  "button",
                  {
                    className: `history-item ${session?.id === item.id ? "active" : ""}`,
                    key: item.id,
                    onClick: () => void handleOpenHistory(item),
                    disabled: isHistoryLoading
                  },
                  <>
                    <span>{scenario?.titleCn || scenario?.title || item.scenarioId}</span>
                    <small>{preview}</small>
                  </>
                );
              })
            ) : (
              <p className="hint">暂无历史练习。</p>
            )}
          </div>
          <div className="review-link">复习中心</div>
        </aside>

        <section className="conversation-panel">
          <div className="conversation-head">
            <div>
              <span className="label">对话</span>
              <h2>{activeScenario?.titleCn || activeScenario?.title || "请选择训练场景"}</h2>
            </div>
            <div className="conversation-tools">
              <span className="scenario-lock">{session ? "场景已锁定" : "先创建对话"}</span>
              <span className="session-pill">{isSessionCompleted ? "已结课" : session ? "练习中" : "未开始"}</span>
            </div>
          </div>

          {error && <div className="error-strip">{error}</div>}
          {scenarios.length === 0 && <p className="hint empty-hint">暂无可用训练场景，请先初始化 scenarios 表数据。</p>}

          <div className="conversation-scroll">
            {turns.length === 0 && (
              <div className="empty-state">
                <span>01</span>
                <h2>先在左侧选好场景，再创建新对话。</h2>
                <p>{activeScenario?.description || "创建对话后，当前对话的场景会被锁定。想换场景，请重新选择场景并创建新对话。"}</p>
                {activeScenario?.suggestedPrompts?.length ? (
                  <div className="prompt-list">
                    <span>可参考表达</span>
                    {activeScenario.suggestedPrompts.slice(0, 3).map((prompt, index) =>
                      createElement("p", { key: `prompt-${index}` }, prompt)
                    )}
                  </div>
                ) : null}
              </div>
            )}

            {turns.flatMap((turn) => [
              createElement(
                "div",
                { className: "message-row user-message", key: `${turn.sessionId}-${turn.turnIndex}-user` },
                <div className="message-stack">
                  <span className="message-name">我</span>
                  <div className="message-bubble">
                    <p>{turn.asr.text || "本轮未识别到有效文本"}</p>
                    <small>ASR confidence {Math.round((turn.asr.confidence ?? 0) * 100)}%</small>
                    <button
                      className={`feedback-toggle ${feedbackTurn?.turnIndex === turn.turnIndex ? "active" : ""}`}
                      type="button"
                      onClick={() => setSelectedFeedbackTurnIndex(turn.turnIndex)}
                    >
                      查看反馈
                    </button>
                  </div>
                </div>
              ),
              createElement(
                "div",
                { className: "message-row assistant-message", key: `${turn.sessionId}-${turn.turnIndex}-assistant` },
                <div className="message-stack">
                  <span className="message-name">AI 陪练</span>
                  <div className="message-bubble">
                    <p>{turn.aiText}</p>
                    {turn.audioUrl && <audio controls src={turn.audioUrl} />}
                  </div>
                </div>
              )
            ])}

            {isBusy && (
              <div className="message-row assistant-message">
                <div className="message-stack">
                  <span className="message-name">AI 陪练</span>
                  <div className="message-bubble pending-bubble">
                    <p>正在处理语音和生成回复...</p>
                  </div>
                </div>
              </div>
            )}

            <div ref={conversationEndRef} />
          </div>

          <div className="record-dock">
            <button className={`record-button ${isRecording ? "recording" : ""}`} onClick={isRecording ? stopRecording : startRecording} disabled={!isRecording && !canStartRecording}>
              {isRecording ? "停止并上传" : session ? "开始录音" : "先创建对话"}
            </button>
            <button className="report-button" onClick={handleReportAction} disabled={!session || isBusy || (!isSessionCompleted && turns.length === 0)}>
              {isSessionCompleted ? "查看课后总结" : "生成课后总结"}
            </button>
          </div>
        </section>

        <aside className="insight-rail">
          <div className="assistant-head">
            <div>
              <span className="label">AI 辅导</span>
              <h2>练习反馈</h2>
            </div>
          </div>

          <div className="score-grid">
            <ScoreDial label="Overall" value={feedbackTurn?.pronunciation.overallScore ?? 0} />
            <ScoreDial label="Pronunciation" value={feedbackTurn?.pronunciation.pronunciationScore ?? 0} />
            <ScoreDial label="Grammar" value={feedbackTurn?.correction.grammarScore ?? 0} />
            <ScoreDial label="Expression" value={feedbackTurn?.correction.expressionScore ?? 0} />
          </div>

          <div className="feedback-box selected-sentence-box">
            <h3>当前句子</h3>
            {feedbackTurn ? (
              <p>{feedbackTurn.asr.text || "本轮未识别到有效文本"}</p>
            ) : (
              <p>点击某条消息的“查看反馈”，这里会显示对应句子的反馈。</p>
            )}
          </div>

          <div className="feedback-box">
            <h3>发音建议</h3>
            {feedbackTurn?.pronunciation.suggestions.length ? (
              feedbackTurn.pronunciation.suggestions.map((item, index) =>
                createElement("p", { key: `pronunciation-${index}` }, item)
              )
            ) : (
              <p>完成一轮录音后查看发音反馈。</p>
            )}
            {feedbackTurn?.pronunciation.issues.length ? (
              feedbackTurn.pronunciation.issues.map((item, index) =>
                createElement("p", { key: `issue-${index}` }, `${item.target}: ${item.message}`)
              )
            ) : null}
          </div>

          <div className="feedback-box">
            <h3>纠错与表达</h3>
            <CorrectionFeedback turn={feedbackTurn} />
          </div>
        </aside>
      </section>

      {report && isReportOpen && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="report-title">
          <section className="report-modal">
            <button className="modal-close" onClick={() => setIsReportOpen(false)} aria-label="关闭课后总结">
              ×
            </button>
            <div className="report-header">
              <span className="label">课后总结</span>
              <h2 id="report-title">本次口语训练报告</h2>
              <p>{report.teacherComment}</p>
            </div>
            <div className="report-score-grid">
              <ScoreDial label="Overall" value={report.overallScore ?? 0} />
              <ScoreDial label="Pronunciation" value={report.pronunciationScore ?? 0} />
              <ScoreDial label="Grammar" value={report.grammarScore ?? 0} />
              <ScoreDial label="Vocabulary" value={report.vocabularyScore ?? 0} />
            </div>
            <div className="report-columns">
              <div>
                <h3>优势</h3>
                {parseJsonList(report.strengths).map((item, index) => createElement("p", { key: `strength-${index}` }, item))}
              </div>
              <div>
                <h3>待提升</h3>
                {parseJsonList(report.weaknesses).map((item, index) => createElement("p", { key: `weakness-${index}` }, item))}
              </div>
            </div>
          </section>
        </div>
      )}

      {isProfileOpen && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="profile-title">
          <section className="report-modal profile-modal">
            <button className="modal-close" onClick={() => setIsProfileOpen(false)} aria-label="关闭用户资料">
              ×
            </button>
            <div className="report-header">
              <span className="label">用户资料</span>
              <h2 id="profile-title">训练档案</h2>
              <p>查看账号信息，并维护用于个性化练习的英语等级。</p>
            </div>
            <div className="profile-card">
              <div>
                <span>用户名</span>
                <strong>{currentUser?.username || username}</strong>
              </div>
              <div>
                <span>当前等级</span>
                <strong>{currentUser?.englishLevel || profileLevel}</strong>
              </div>
            </div>
            <div className="profile-form">
              <label>
                邮箱
                <input value={profileEmail} onChange={(event) => setProfileEmail(event.target.value)} placeholder="可选" disabled={isProfileSaving} />
              </label>
              <label>
                英语等级
                <select value={profileLevel} onChange={(event) => setProfileLevel(event.target.value)} disabled={isProfileSaving}>
                  <option value="BEGINNER">BEGINNER</option>
                  <option value="INTERMEDIATE">INTERMEDIATE</option>
                  <option value="ADVANCED">ADVANCED</option>
                </select>
              </label>
              <button className="ghost-button" onClick={handleSaveProfile} disabled={isProfileSaving}>
                {isProfileSaving ? "保存中..." : "保存资料"}
              </button>
            </div>
          </section>
        </div>
      )}

    </main>
  );
}

function turnToHistory(turn: Turn): ConversationTurnHistory {
  return {
    id: `${turn.sessionId}-${turn.turnIndex}`,
    turnIndex: turn.turnIndex,
    userText: turn.asr.text || "本轮未识别到有效文本",
    aiText: turn.aiText,
    pronunciationScore: turn.pronunciation.pronunciationScore,
    fluencyScore: turn.pronunciation.fluencyScore,
    grammarIssues: turn.correction.corrections.length
      ? JSON.stringify(turn.correction.corrections)
      : turn.correction.overallFeedback
  };
}

function cacheSessionDetail(session: PracticeSession, turns: Turn[]) {
  if (!turns.length) return;
  const cache = readHistoryCache();
  cache[session.id] = {
    ...session,
    turns: turns.map(turnToHistory)
  };
  localStorage.setItem(HISTORY_CACHE_KEY, JSON.stringify(cache));
}

function normalizePracticeSession(session: PracticeSession, scenario: Scenario): PracticeSession {
  return {
    ...session,
    id: session.id,
    scenarioId: session.scenarioId || scenario.id,
    status: session.status || "ACTIVE",
    difficulty: session.difficulty || scenario.difficulty || "BEGINNER"
  };
}

function upsertHistorySession(sessions: PracticeSession[], session: PracticeSession) {
  return [session, ...sessions.filter((item) => item.id !== session.id)];
}

function readCachedSessionDetail(sessionId: string): SessionDetail | null {
  return readHistoryCache()[sessionId] ?? null;
}

function readHistoryCache(): Record<string, SessionDetail> {
  const raw = localStorage.getItem(HISTORY_CACHE_KEY);
  if (!raw) return {};
  try {
    return JSON.parse(raw) as Record<string, SessionDetail>;
  } catch {
    localStorage.removeItem(HISTORY_CACHE_KEY);
    return {};
  }
}

function historyTurnToVoiceTurn(sessionId: string, turn: ConversationTurnHistory): Turn {
  const pronunciationScore = Number(turn.pronunciationScore ?? 0);
  const fluencyScore = Number(turn.fluencyScore ?? 0);
  const overallScore = pronunciationScore || fluencyScore
    ? Math.round((pronunciationScore + fluencyScore) / (pronunciationScore && fluencyScore ? 2 : 1))
    : 0;
  const words = turn.userText.trim().split(/\s+/).filter(Boolean);
  const corrections = parseCorrectionItems(turn.grammarIssues);

  return {
    sessionId,
    turnIndex: turn.turnIndex,
    asr: {
      text: turn.userText,
      confidence: 0,
      durationMs: 0,
      finalResult: true
    },
    aiText: turn.aiText,
    tts: {
      text: turn.aiText,
      voice: "",
      format: "",
      mimeType: "",
      durationMs: 0,
      audioContentBase64: ""
    },
    pronunciation: {
      pronunciationScore,
      fluencyScore,
      paceScore: 0,
      overallScore,
      wordCount: words.length,
      wordsPerMinute: 0,
      suggestions: [],
      issues: []
    },
    correction: {
      correctedText: turn.userText,
      grammarScore: corrections.length ? 70 : 0,
      expressionScore: 0,
      overallFeedback: buildNaturalFeedback(turn.grammarIssues, corrections),
      corrections,
      betterExpressions: []
    }
  };
}

function CorrectionFeedback({ turn }: { turn?: Turn }) {
  if (!turn) {
    return <p>点击某条消息的“查看反馈”，这里会显示对应句子的纠错与表达建议。</p>;
  }

  const corrections = turn.correction.corrections.length
    ? turn.correction.corrections
    : parseCorrectionItems(turn.correction.overallFeedback);
  const feedback = buildNaturalFeedback(turn.correction.overallFeedback, corrections);

  return (
    <>
      <p>{feedback}</p>
      {corrections.length ? (
        <div className="correction-list">
          {corrections.map((item, index) => (
            <div className="correction-card" key={`${item.type}-${item.original}-${index}`}>
              <span>{correctionTypeLabel(item.type)}</span>
              <strong>{formatCorrectionSuggestion(item)}</strong>
              {item.explanation && <p>{item.explanation}</p>}
            </div>
          ))}
        </div>
      ) : null}
      {turn.correction.betterExpressions.length ? (
        <div className="expression-list">
          <span>更自然的表达</span>
          {turn.correction.betterExpressions.map((item, index) =>
            createElement("p", { key: `expression-${index}` }, item)
          )}
        </div>
      ) : null}
    </>
  );
}

function parseCorrectionItems(raw?: string): CorrectionItem[] {
  if (!raw || !looksLikeJson(raw)) return [];
  try {
    const parsed = JSON.parse(raw);
    const source: unknown[] = Array.isArray(parsed)
      ? parsed
      : Array.isArray(parsed?.corrections)
        ? parsed.corrections
        : [];
    return source.map(coerceCorrectionItem).filter((item): item is CorrectionItem => Boolean(item));
  } catch {
    return [];
  }
}

function coerceCorrectionItem(value: any): CorrectionItem | null {
  if (!value || typeof value !== "object") return null;
  const original = stringValue(value.original);
  const corrected = stringValue(value.corrected);
  const explanation = stringValue(value.explanation);
  if (!original && !corrected && !explanation) return null;
  return {
    type: stringValue(value.type) || "expression",
    original,
    corrected,
    explanation,
    severity: stringValue(value.severity) || "medium"
  };
}

function buildNaturalFeedback(raw: string | undefined, corrections: CorrectionItem[]) {
  if (corrections.length > 0) {
    return corrections.length === 1
      ? "这句话有 1 处可以优化，下面是具体建议。"
      : `这句话有 ${corrections.length} 处可以优化，下面是具体建议。`;
  }
  if (raw && !looksLikeJson(raw)) {
    return raw;
  }
  return "这句话暂时没有发现明显的语法或表达问题。";
}

function formatCorrectionSuggestion(item: CorrectionItem) {
  if (item.original && item.corrected) {
    return `建议把 “${item.original}” 改成 “${item.corrected}”。`;
  }
  if (item.corrected) {
    return `可以表达为 “${item.corrected}”。`;
  }
  return "这处表达可以再自然一些。";
}

function correctionTypeLabel(type: string) {
  const normalized = type.toLowerCase();
  if (normalized.includes("spell")) return "拼写建议";
  if (normalized.includes("punct")) return "标点建议";
  if (normalized.includes("grammar")) return "语法建议";
  return "表达建议";
}

function looksLikeJson(value: string) {
  const trimmed = value.trim();
  return trimmed.startsWith("[") || trimmed.startsWith("{");
}

function stringValue(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}

function ScoreDial({ label, value }: { label: string; value: number }) {
  return (
    <div className="score-dial" style={{ "--score": `${Math.max(0, Math.min(100, value))}%` } as React.CSSProperties}>
      <strong>{Math.round(value)}</strong>
      <span>{label}</span>
    </div>
  );
}

function readStoredUser(): UserProfile | null {
  const raw = localStorage.getItem("smartVoiceUser");
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserProfile;
  } catch {
    localStorage.removeItem("smartVoiceUser");
    return null;
  }
}
