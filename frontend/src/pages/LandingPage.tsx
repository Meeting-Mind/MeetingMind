import { useEffect } from "react";
import { Link } from "react-router-dom";

const flowSteps = [
  {
    number: "01",
    label: "Meeting",
    title: "Capture the conversation.",
    description: "Keep live dialogue and decisions inside the context of the current meeting.",
    output: "Live Transcript"
  },
  {
    number: "02",
    label: "Report",
    title: "Confirm the decisions.",
    description: "Review agreements, decisions, and follow-up work in the confirmed report.",
    output: "Confirmed Report"
  },
  {
    number: "03",
    label: "Project Knowledge",
    title: "Carry it into the next task.",
    description: "Reconnect tasks and official knowledge inside the project space.",
    output: "Project Knowledge"
  }
];

const operatingPrinciples = [
  {
    label: "After the meeting ends",
    title: "Decisions get scattered.",
    description: "Conversation stays in one tool, tasks move to another board, and rationale often ends up in private notes."
  },
  {
    label: "Inside MeetingMind",
    title: "Work context stays connected.",
    description: "Conversation becomes transcript and report, and confirmed decisions flow into tasks and project knowledge."
  },
  {
    label: "When you need it again",
    title: "Start from the source.",
    description: "AI only searches permitted meetings and official knowledge, then shows the source with every answer."
  }
];

export function LandingPage() {
  useEffect(() => {
    document.body.className = "landing-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  return (
    <main className="landing-page" id="top">
      <header className="landing-header landing-header--dark">
        <div className="landing-container landing-header-inner">
          <a className="landing-brand" href="#top" aria-label="MeetingMind home">
            <span className="landing-brand-wordmark">Meeting<span>Mind</span></span>
          </a>

          <div className="landing-header-actions">
            <Link className="landing-text-link" to="/meeting-access">Join meeting</Link>
            <Link className="landing-text-link" to="/spaces">Open workspace</Link>
          </div>
        </div>
      </header>

      <section className="landing-hero landing-hero--dark" id="overview" aria-labelledby="landing-hero-title">
        <img className="landing-hero-backdrop" src="/meetingmind-wave.png" alt="" aria-hidden="true" />
        <div className="landing-container landing-hero-grid">
          <div className="landing-hero-copy">
            <p className="landing-hero-kicker">From meeting to execution</p>
            <h1 id="landing-hero-title"><span className="landing-hero-phrase">Keep work moving</span> <span className="landing-hero-phrase"><em>after the meeting ends.</em></span></h1>
            <p className="landing-hero-lead">
              Turn meeting conversations into reports, decisions, tasks, and project knowledge your team can reuse.
            </p>
          </div>

          <figure className="landing-hero-visual">
            <img src="/meetingmind-hero.png" alt="MeetingMind workspace connecting meeting discussion to decisions, tasks, and project knowledge" />
          </figure>
        </div>
        <a className="landing-scroll-cue" href="#landing-flow" aria-label="Move to workflow section">
          <span aria-hidden="true">↓</span>
        </a>
      </section>

      <section className="landing-problem-section" aria-labelledby="landing-problem-title">
        <div className="landing-container">
          <div className="landing-section-intro landing-problem-intro">
            <h2 id="landing-problem-title">Meetings should become <em>evidence for the next action</em>, not just notes.</h2>
            <p>MeetingMind organizes conversation, decisions, and work into one project flow.</p>
          </div>
          <div className="landing-principle-grid">
            {operatingPrinciples.map((principle, index) => (
              <article className="landing-principle" key={principle.title}>
                <span className="landing-principle-index">0{index + 1}</span>
                <span className="landing-principle-label">{principle.label}</span>
                <h3>{principle.title}</h3>
                <p>{principle.description}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="landing-products-section" aria-labelledby="landing-products-title">
        <div className="landing-container">
          <div className="landing-section-intro landing-products-intro">
            <h2 id="landing-products-title">What MeetingMind connects</h2>
            <p>Capture meetings, build project knowledge, and find it again when the team needs it.</p>
          </div>
          <div className="landing-product-grid">
            <article className="landing-product-card landing-product-card--featured landing-product-card--meeting">
              <div className="landing-product-image"><img src="/meetingmind-meeting-v2.png" alt="MeetingMind screen showing live meeting dialogue and a confirmed report together" /></div>
              <div className="landing-product-copy"><span>Meeting workspace</span><h3>Capture conversation and decisions.</h3><p>See live meeting context and the confirmed report in one workflow.</p></div>
            </article>
            <article className="landing-product-card landing-product-card--report">
              <div className="landing-product-image"><img src="/meetingmind-report-v2.png" alt="Confirmed report screen summarizing decisions and next actions" /></div>
              <div className="landing-product-copy"><span>Confirmed report</span><h3>Keep the outcome of the meeting.</h3><p>Turn agreements and next actions into a shared team reference.</p></div>
            </article>
            <article className="landing-product-card landing-product-card--ai">
              <div className="landing-product-image"><img src="/meetingmind-ai-search-v2.png" alt="MeetingMind AI search screen with permission scope and citations" /></div>
              <div className="landing-product-copy"><span>AI search</span><h3>Find answers with evidence.</h3><p>Search only accessible records and show citations with every answer.</p></div>
            </article>
            <article className="landing-product-card landing-product-card--knowledge">
              <div className="landing-product-image"><img src="/meetingmind-knowledge-v2.png" alt="Project knowledge screen connected to meeting records and tasks" /></div>
              <div className="landing-product-copy"><span>Project knowledge</span><h3>Build a reusable source of truth.</h3><p>Keep decisions and tasks from meetings available inside the project space.</p></div>
            </article>
            <article className="landing-product-card landing-product-card--task">
              <div className="landing-product-image"><img src="/meetingmind-task-v2.png" alt="Kanban screen managing tasks created from meetings" /></div>
              <div className="landing-product-copy"><span>Tasks</span><h3>Move directly into execution.</h3><p>Track work created in meetings with assignees and clear status.</p></div>
            </article>
          </div>
        </div>
      </section>

      <section className="landing-flow-section" id="landing-flow" aria-labelledby="landing-flow-title">
        <div className="landing-container">
          <div className="landing-section-intro">
            <h2 id="landing-flow-title">One meeting becomes the <em>start of the next task</em>.</h2>
            <p>Do more than archive notes. Turn them into a workflow the team can reuse.</p>
          </div>
          <ol className="landing-flow-list">
            {flowSteps.map((step, index) => (
              <li className="landing-flow-item" key={step.number}>
                <div className="landing-flow-index"><span>{step.number}</span>{index < flowSteps.length - 1 ? <i aria-hidden="true" /> : null}</div>
                <div className="landing-flow-copy"><span className="landing-flow-label">{step.label}</span><h3>{step.title}</h3><p>{step.description}</p></div>
                <span className="landing-flow-output">{step.output}</span>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="landing-access-section" id="landing-access" aria-labelledby="landing-access-title">
        <div className="landing-container">
          <div className="landing-section-intro landing-section-intro--wide"><h2 id="landing-access-title">AI answers only <em>within the allowed scope.</em></h2><p>Meeting AI searches only the current meeting. Project AI searches only permitted meetings and official knowledge. Every answer includes a source.</p></div>
          <div className="landing-access-grid">
            <article className="landing-access-card"><div className="landing-access-card-heading"><span className="landing-access-icon">M</span><div><span className="landing-access-type">CURRENT MEETING</span><h3>Meeting AI</h3></div></div><p>Uses only the STT, report, and decisions from the meeting you are viewing now.</p><div className="landing-access-footer"><span>Current meeting scope</span><span className="landing-access-check">✓ Restricted</span></div></article>
            <article className="landing-access-card"><div className="landing-access-card-heading"><span className="landing-access-icon landing-access-icon--blue">P</span><div><span className="landing-access-type">PROJECT SPACE</span><h3>Project AI</h3></div></div><p>Searches only accessible meetings and official project knowledge.</p><div className="landing-access-footer"><span>Space permission scope</span><span className="landing-access-check">✓ Verified</span></div></article>
          </div>
        </div>
      </section>

      <section className="landing-final-cta" aria-labelledby="landing-final-cta-title">
        <div className="landing-container landing-final-cta-inner"><div><h2 id="landing-final-cta-title">Start the next meeting on top of the last decision.</h2></div><div className="landing-final-actions"><Link className="landing-button landing-button--light" to="/spaces">Open workspace <span aria-hidden="true">↗</span></Link><Link className="landing-light-link" to="/meeting-access">Join meeting</Link></div></div>
      </section>

      <footer className="landing-footer"><div className="landing-container"><span className="landing-brand-wordmark">Meeting<span>Mind</span></span><span>Turn meetings into project knowledge.</span></div></footer>
    </main>
  );
}
