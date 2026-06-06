import { createElement, useEffect, useRef, useState } from "react";
import { base64ToAudioUrl, encodeWavFromFloat32, parseJsonList } from "./audio";
import { createSession, generateReport, listScenarios, login, sendVoiceDialogue } from "./api";
import type { AuthResponse, PracticeSession, Scenario, SessionReport, VoiceDialogueResponse } from "./types";

type Turn = VoiceDialogueResponse & {
  audioUrl?: string;
};

type WebAudioWindow = Window & {
  webkitAudioContext?: typeof AudioContext;
};

type UserProfile = AuthResponse["user"];

export default function App() {
  const [token, setToken] = useState(localStorage.getItem("smartVoiceToken") ?? "");
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(() => readStoredUser());
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [selectedScenarioId, setSelectedScenarioId] = useState("");
  const [session, setSession] = useState<PracticeSession | null>(null);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [report, setReport] = useState<SessionReport | null>(null);
  const [isReportOpen, setIsReportOpen] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isBusy, setIsBusy] = useState(false);
  const [error, setError] = useState("");
  const [recordStartedAt, setRecordStartedAt] = useState<number | null>(null);

  const audioContextRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Float32Array[]>([]);
  const conversationEndRef = useRef<HTMLDivElement | null>(null);

  const selectedScenario = scenarios.find((scenario) => scenario.id === selectedScenarioId);
  const latestTurn = turns.length > 0 ? turns[turns.length - 1] : undefined;
  const isSessionCompleted = session?.status === "COMPLETED";

  useEffect(() => {
    if (!token) return;
    listScenarios(token)
      .then((data) => {
        setScenarios(data);
        setSelectedScenarioId((current) => current || data[0]?.id || "");
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Failed to load scenarios"));
  }, [token]);

  useEffect(() => {
    conversationEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [turns.length, isBusy]);

  async function handleLogin() {
    setError("");
    if (!username.trim() || !password.trim()) {
      setError("请输入用户名和密码。");
      return;
    }
    setIsBusy(true);
    try {
      const auth = await login(username.trim(), password);
      localStorage.setItem("smartVoiceToken", auth.accessToken);
      localStorage.setItem("smartVoiceUser", JSON.stringify(auth.user));
      setToken(auth.accessToken);
      setCurrentUser(auth.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
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
    setReport(null);
    setIsReportOpen(false);
    setError("");
  }

  async function handleCreateSession() {
    if (!selectedScenarioId) return;
    setError("");
    setIsBusy(true);
    setReport(null);
    setIsReportOpen(false);
    setTurns([]);
    try {
      const created = await createSession(token, selectedScenarioId, selectedScenario?.difficulty ?? "BEGINNER");
      setSession(created);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create session");
    } finally {
      setIsBusy(false);
    }
  }

  async function startRecording() {
    if (!session) {
      setError("Create a session first.");
      return;
    }
    if (isSessionCompleted) {
      setError("本次会话已生成报告，请创建新会话后继续录音。");
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
        referenceText: selectedScenario?.suggestedPrompts?.[0],
        durationMs,
        voice: "zhixiaobai"
      });
      const audioUrl = response.tts?.audioContentBase64
        ? base64ToAudioUrl(response.tts.audioContentBase64, response.tts.mimeType)
        : undefined;
      setTurns((current) => [...current, { ...response, audioUrl }]);
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
      setSession((current) => current ? { ...current, status: "COMPLETED" } : current);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to generate report");
    } finally {
      setIsBusy(false);
    }
  }

  if (!token) {
    return (
      <main className="login-page">
        <section className="login-copy">
          <p className="eyebrow">AI 英语口语陪练</p>
          <h1>进入你的场景口语训练</h1>
          <p>登录后选择面试、点餐、会议等场景，录音对话并查看发音、纠错和课后总结。</p>
        </section>

        <section className="login-panel">
          <h2>登录</h2>
          {error && <div className="error-strip">{error}</div>}
          <label>
            用户名
            <input value={username} onChange={(event) => setUsername(event.target.value)} />
          </label>
          <label>
            密码
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          <button onClick={handleLogin} disabled={isBusy || !username.trim() || !password.trim()}>
            {isBusy ? "登录中..." : "进入训练"}
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <section className="hero-band">
        <div>
          <p className="eyebrow">AI 英语口语陪练</p>
          <h1>场景口语训练工作台</h1>
        </div>
        <div className="signal-card">
          <span>{session ? "会话进行中" : "欢迎回来"}</span>
          <strong>{turns.length}</strong>
          <small>轮对话已完成</small>
        </div>
      </section>

      <section className="workspace">
        <aside className="control-panel">
          <div className="profile-block">
            <span>欢迎，{currentUser?.username || username}</span>
            <small>{currentUser?.englishLevel || "ENGLISH"} 训练档案</small>
            <button className="ghost-button" onClick={handleLogout} disabled={isBusy || isRecording}>
              退出登录
            </button>
          </div>

          <div className="panel-block">
            <h2>训练场景</h2>
            <select value={selectedScenarioId} onChange={(event) => setSelectedScenarioId(event.target.value)}>
              {scenarios.map((scenario) => createElement("option", { key: scenario.id, value: scenario.id }, scenario.titleCn || scenario.title))}
            </select>
            {selectedScenario && <p className="muted">{selectedScenario.description}</p>}
            {selectedScenario?.suggestedPrompts?.length ? (
              <div className="prompt-list">
                <span>推荐开场句</span>
                {selectedScenario.suggestedPrompts.slice(0, 3).map((prompt, index) =>
                  createElement("p", { key: `prompt-${index}` }, prompt)
                )}
              </div>
            ) : null}
            <button onClick={handleCreateSession} disabled={!token || isBusy || !selectedScenarioId}>
              创建会话
            </button>
          </div>

          <div className="panel-block">
            <h2>录音</h2>
            <button className={isRecording ? "recording" : ""} onClick={isRecording ? stopRecording : startRecording} disabled={!session || isBusy || isSessionCompleted}>
              {isRecording ? "停止并上传" : "开始录音"}
            </button>
            <p className="hint">
              {isSessionCompleted
                ? "本次会话已结课。要继续练习，请创建新会话。"
                : "浏览器会录制并转成后端 ASR 要求的 16kHz 单声道 16-bit WAV。"}
            </p>
          </div>

          <button className="report-button" onClick={handleGenerateReport} disabled={!session || turns.length === 0 || isBusy}>
            生成课后总结
          </button>
        </aside>

        <section className="practice-stage">
          {error && <div className="error-strip">{error}</div>}

          <div className="conversation">
            <div className="conversation-head">
              <div>
                <span className="label">对话区</span>
                <h2>{selectedScenario?.titleCn || selectedScenario?.title || "请选择训练场景"}</h2>
              </div>
              <span className="session-pill">{isSessionCompleted ? "已结课" : session ? "练习中" : "未开始"}</span>
            </div>

            {turns.length === 0 && (
              <div className="empty-state">
                <span>01</span>
                <h2>选择场景后开始录音，完成第一轮真实对话。</h2>
                <p>页面会展示语音识别文本、AI 回复、TTS 播放、发音评分、语法纠错和课后报告。</p>
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

          <div className="insight-rail">
            <ScoreDial label="Overall" value={latestTurn?.pronunciation.overallScore ?? 0} />
            <ScoreDial label="Pronunciation" value={latestTurn?.pronunciation.pronunciationScore ?? 0} />
            <ScoreDial label="Grammar" value={latestTurn?.correction.grammarScore ?? 0} />
            <ScoreDial label="Expression" value={latestTurn?.correction.expressionScore ?? 0} />

            <div className="feedback-box">
              <h3>发音建议</h3>
              {latestTurn?.pronunciation.suggestions.length ? (
                latestTurn.pronunciation.suggestions.map((item, index) =>
                  createElement("p", { key: `pronunciation-${index}` }, item)
                )
              ) : (
                <p>完成一轮录音后查看发音反馈。</p>
              )}
              {latestTurn?.pronunciation.issues.length ? (
                latestTurn.pronunciation.issues.map((item, index) =>
                  createElement("p", { key: `issue-${index}` }, `${item.target}: ${item.message}`)
                )
              ) : null}
            </div>

            <div className="feedback-box">
              <h3>纠错与表达</h3>
              {latestTurn?.correction.overallFeedback && <p>{latestTurn.correction.overallFeedback}</p>}
              {latestTurn?.correction.corrections.length ? (
                latestTurn.correction.corrections.map((item: NonNullable<Turn["correction"]["corrections"]>[number], index: number) =>
                  createElement("p", { key: `${item.type}-${index}` }, <><strong>{item.original}</strong> -&gt; {item.corrected}</>)
                )
              ) : (
                <p>完成一轮录音后查看语法与表达纠错。</p>
              )}
              {latestTurn?.correction.betterExpressions.length ? (
                latestTurn.correction.betterExpressions.map((item, index) =>
                  createElement("p", { key: `expression-${index}` }, item)
                )
              ) : null}
            </div>

          </div>
        </section>
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
    </main>
  );
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
