import { useEffect } from "react";
import { Link } from "react-router-dom";

export function LandingPage() {
  useEffect(() => {
    document.body.className = "landing-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  return (
    <>
      <header className="site-header">
        <div className="container header-inner">
          <a className="brand" href="#top">
            <span className="brand-copy">
              <span className="brand-wordmark-main">meeting</span>
              <span className="brand-wordmark-accent">mind</span>
            </span>
          </a>
          <nav className="main-nav" id="mainNav">
            <a href="#overview">개요</a>
            <a href="#features">주요 기능</a>
            <a href="#features">사용 흐름</a>
            <a href="#features">권한/보관</a>
          </nav>
        </div>
      </header>

      <main id="top">
        <section className="hero" id="overview">
          <div className="container hero-grid">
            <div className="hero-copy">
              <p className="eyebrow">MeetingMind Service Overview</p>
              <h1>회의에서 나온 결정과 업무를 한곳에서 이어서 확인할 수 있습니다.</h1>
              <p className="hero-text">
                MeetingMind는 회의 기록을 남기는 데서 끝나지 않고, 회차마다 나온 논의 내용과
                결정사항, 담당 업무를 프로젝트 흐름 안에서 정리하고 다시 찾아볼 수 있게 돕는 협업
                서비스입니다.
              </p>
              <div className="cta-row">
                <Link className="btn btn-solid" to="/spaces">
                  워크스페이스 보기
                </Link>
                <a className="btn btn-ghost" href="#flow">
                  사용자 흐름 보기
                </a>
              </div>
              <div className="preview-chips">
                <span>프로젝트별 회의 관리</span>
                <span>현재 회의 내용 빠르게 확인</span>
                <span>여러 회차 흐름 이어서 보기</span>
              </div>
            </div>
            <div className="hero-marquee">
              <div className="marquee-column drift-slow">
                <div className="marquee-stack">
                  <Link className="preview-card tall clean-card" to="/live-meeting">
                    <div className="preview-card-head">
                      <span>Live Meeting</span>
                      <span>Ready</span>
                    </div>
                    <div className="preview-stage warm">
                      <div className="stage-orb orange" />
                      <div className="stage-curve" />
                    </div>
                    <div className="preview-lines">
                      <span className="w-90" />
                      <span className="w-70" />
                    </div>
                  </Link>
                  <Link className="preview-card compact chip-card" to="/project-overview">
                    <div className="preview-card-head">
                      <span>Space Members</span>
                      <span>6명</span>
                    </div>
                    <div className="preview-chips">
                      <span>PM</span>
                      <span>Dev</span>
                      <span>Design</span>
                    </div>
                  </Link>
                  <Link className="preview-card medium transcript-card" to="/meeting-ai">
                    <div className="preview-card-head">
                      <span>회의 내용 자세히 보기</span>
                      <span>현재 회의</span>
                    </div>
                    <div className="preview-feed">
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                  </Link>
                </div>
                <div className="marquee-stack" aria-hidden="true">
                  <Link className="preview-card tall clean-card" to="/live-meeting">
                    <div className="preview-card-head">
                      <span>Live Meeting</span>
                      <span>Ready</span>
                    </div>
                    <div className="preview-stage warm">
                      <div className="stage-orb orange" />
                      <div className="stage-curve" />
                    </div>
                    <div className="preview-lines">
                      <span className="w-90" />
                      <span className="w-70" />
                    </div>
                  </Link>
                  <Link className="preview-card compact chip-card" to="/project-overview">
                    <div className="preview-card-head">
                      <span>Space Members</span>
                      <span>6명</span>
                    </div>
                    <div className="preview-chips">
                      <span>PM</span>
                      <span>Dev</span>
                      <span>Design</span>
                    </div>
                  </Link>
                  <Link className="preview-card medium transcript-card" to="/meeting-ai">
                    <div className="preview-card-head">
                      <span>회의 내용 자세히 보기</span>
                      <span>현재 회의</span>
                    </div>
                    <div className="preview-feed">
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                  </Link>
                </div>
              </div>

              <div className="marquee-column drift-fast">
                <div className="marquee-stack">
                  <Link className="preview-card medium airy-card" to="/report-agent">
                    <div className="preview-card-head">
                      <span>Report Agent</span>
                      <span>Draft v3</span>
                    </div>
                    <div className="preview-lines">
                      <span className="w-80" />
                      <span className="w-100" />
                      <span className="w-60" />
                    </div>
                  </Link>
                  <Link className="preview-card tall dashboard-card" to="/project-overview">
                    <div className="preview-card-head">
                      <span>Project Overview</span>
                      <span>Today</span>
                    </div>
                    <div className="preview-grid">
                      <span />
                      <span />
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                  </Link>
                  <Link className="preview-card compact accent-card" to="/meeting-ai">
                    <div className="preview-card-head">
                      <span>프로젝트 전체 흐름 보기</span>
                      <span>권한 반영</span>
                    </div>
                    <div className="preview-prompt">"우리가 왜 PostgreSQL을 선택했어?"</div>
                  </Link>
                </div>
                <div className="marquee-stack" aria-hidden="true">
                  <Link className="preview-card medium airy-card" to="/report-agent">
                    <div className="preview-card-head">
                      <span>Report Agent</span>
                      <span>Draft v3</span>
                    </div>
                    <div className="preview-lines">
                      <span className="w-80" />
                      <span className="w-100" />
                      <span className="w-60" />
                    </div>
                  </Link>
                  <Link className="preview-card tall dashboard-card" to="/project-overview">
                    <div className="preview-card-head">
                      <span>Project Overview</span>
                      <span>Today</span>
                    </div>
                    <div className="preview-grid">
                      <span />
                      <span />
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                  </Link>
                  <Link className="preview-card compact accent-card" to="/meeting-ai">
                    <div className="preview-card-head">
                      <span>프로젝트 전체 흐름 보기</span>
                      <span>권한 반영</span>
                    </div>
                    <div className="preview-prompt">"우리가 왜 PostgreSQL을 선택했어?"</div>
                  </Link>
                </div>
              </div>

              <div className="marquee-column drift-slower">
                <div className="marquee-stack">
                  <Link className="preview-card compact dark-card" to="/meeting-ai">
                    <div className="preview-card-head">
                      <span>지금 회의만 찾기</span>
                      <span>3회차 전용</span>
                    </div>
                    <div className="preview-prompt">지금 보고 있는 회의의 대화와 보고서만 빠르게 확인</div>
                  </Link>
                  <Link className="preview-card tall transcript-card" to="/live-meeting">
                    <div className="preview-card-head">
                      <span>Real-Time STT</span>
                      <span>Live</span>
                    </div>
                    <div className="preview-feed">
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                  </Link>
                  <Link className="preview-card medium clean-card" to="/report-agent">
                    <div className="preview-card-head">
                      <span>Decision Table</span>
                      <span>Auto</span>
                    </div>
                    <div className="preview-prompt">결정사항, Action Item, 담당자를 보고서로 정리</div>
                  </Link>
                </div>
                <div className="marquee-stack" aria-hidden="true">
                  <Link className="preview-card compact dark-card" to="/meeting-ai">
                    <div className="preview-card-head">
                      <span>지금 회의만 찾기</span>
                      <span>3회차 전용</span>
                    </div>
                    <div className="preview-prompt">지금 보고 있는 회의의 대화와 보고서만 빠르게 확인</div>
                  </Link>
                  <Link className="preview-card tall transcript-card" to="/live-meeting">
                    <div className="preview-card-head">
                      <span>Real-Time STT</span>
                      <span>Live</span>
                    </div>
                    <div className="preview-feed">
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                  </Link>
                  <Link className="preview-card medium clean-card" to="/report-agent">
                    <div className="preview-card-head">
                      <span>Decision Table</span>
                      <span>Auto</span>
                    </div>
                    <div className="preview-prompt">결정사항, Action Item, 담당자를 보고서로 정리</div>
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="core-features" id="features">
          <div className="container">
            <div className="section-heading">
              <p className="eyebrow">Key Features</p>
            </div>
            <div className="feature-story-list">
              <article className="feature-story">
                <div className="feature-story-copy">
                  <p className="feature-story-kicker">01. 실시간 회의 기록</p>
                  <h3>회의 중 나온 내용을 바로 정리 가능한 형태로 남깁니다.</h3>
                  <p>
                    회의에 입장하면 발화자 기반 자막이 생성되고, 회의가 끝난 뒤 다시 확인할 수 있도록
                    대화 흐름이 정리됩니다.
                  </p>
                </div>
                <Link className="feature-story-visual warm" to="/live-meeting">
                  <div className="feature-illustration meeting-room-visual">
                    <span className="feature-window" />
                    <span className="feature-person left" />
                    <span className="feature-person center" />
                    <span className="feature-person right" />
                    <span className="feature-table" />
                  </div>
                </Link>
              </article>

              <article className="feature-story reverse">
                <Link className="feature-story-visual cool" to="/meeting-ai">
                  <div className="feature-illustration search-visual">
                    <span className="feature-panel main" />
                    <span className="feature-avatar a" />
                    <span className="feature-avatar b" />
                    <span className="feature-avatar c" />
                    <span className="feature-searchbar" />
                  </div>
                </Link>
                <div className="feature-story-copy">
                  <p className="feature-story-kicker">02. 현재 회의 빠르게 다시 보기</p>
                  <h3>지금 보고 있는 회의에서 누가 무엇을 말했는지 바로 찾을 수 있습니다.</h3>
                  <p>
                    현재 회의에 한정된 내용만 빠르게 확인할 수 있어, 긴 기록을 다시 훑지 않아도 필요한
                    논의와 결정사항을 바로 찾아볼 수 있습니다.
                  </p>
                </div>
              </article>

              <article className="feature-story">
                <div className="feature-story-copy">
                  <p className="feature-story-kicker">03. 회의 후 보고서 정리</p>
                  <h3>회의 결과를 보고서와 프로젝트 문서 흐름으로 자연스럽게 이어갑니다.</h3>
                  <p>
                    회의 요약, 결정사항, 할 일을 자동으로 정리하고, 필요한 표현만 다듬어 다음 회의와
                    프로젝트 문서 관리까지 이어서 사용할 수 있습니다.
                  </p>
                </div>
                <Link className="feature-story-visual fresh" to="/report-agent">
                  <div className="feature-illustration report-visual">
                    <span className="feature-doc" />
                    <span className="feature-line short" />
                    <span className="feature-line" />
                    <span className="feature-line" />
                    <span className="feature-badge" />
                  </div>
                </Link>
              </article>

              <article className="feature-policy">
                <div>
                  <p className="feature-story-kicker">04. 권한과 보관 정책</p>
                  <h3>모든 회의를 모두에게 열지 않고, 필요한 정보만 안전하게 이어서 봅니다.</h3>
                </div>
                <ul className="feature-policy-list">
                  <li>회의는 회차별로 분리되고 지정된 참여자만 접근할 수 있습니다.</li>
                  <li>프로젝트 전체 보기에서는 내가 권한 있는 회의 내용만 함께 확인할 수 있습니다.</li>
                  <li>STT 원문은 관리자 보관 정책에 따라 기간을 정해 관리할 수 있습니다.</li>
                </ul>
              </article>
            </div>
          </div>
        </section>

        <section className="social-proof">
          <div className="container">
            <div className="section-heading centered">
              <p className="eyebrow">User Reviews</p>
              <h2>MeetingMind</h2>
              <h2>회의를 더 스마트하게</h2>
            </div>
            <div className="review-marquee">
              <div className="review-track">
                {[
                  {
                    role: "프로젝트 매니저",
                    rating: "★★★★★",
                    quote:
                      "회의가 끝난 뒤 결정사항과 담당 업무를 다시 정리하는 시간이 확실히 줄었습니다."
                  },
                  {
                    role: "개발 리드",
                    rating: "★★★★★",
                    quote:
                      "여러 회차의 논의 흐름을 이어서 확인할 수 있어서 이전 결정을 다시 찾기 쉬웠습니다."
                  },
                  {
                    role: "기획자",
                    rating: "★★★★★",
                    quote:
                      "현재 회의 내용만 빠르게 확인할 수 있어서 긴 기록을 전부 다시 읽지 않아도 됩니다."
                  },
                  {
                    role: "운영 담당자",
                    rating: "★★★★★",
                    quote:
                      "권한이 있는 회의만 볼 수 있어 프로젝트 문서를 공유할 때도 부담이 적었습니다."
                  }
                ].map((review, index) => (
                  <article key={`${review.role}-${index}`} className="review-card">
                    <div className="review-avatar">{review.role.slice(0, 2)}</div>
                    <strong>{review.role}</strong>
                    <div className="review-rating">{review.rating}</div>
                    <p>{review.quote}</p>
                  </article>
                ))}
              </div>
              <div className="review-track" aria-hidden="true">
                {[
                  {
                    role: "프로젝트 매니저",
                    rating: "★★★★★",
                    quote:
                      "회의가 끝난 뒤 결정사항과 담당 업무를 다시 정리하는 시간이 확실히 줄었습니다."
                  },
                  {
                    role: "개발 리드",
                    rating: "★★★★★",
                    quote:
                      "여러 회차의 논의 흐름을 이어서 확인할 수 있어서 이전 결정을 다시 찾기 쉬웠습니다."
                  },
                  {
                    role: "기획자",
                    rating: "★★★★★",
                    quote:
                      "현재 회의 내용만 빠르게 확인할 수 있어서 긴 기록을 전부 다시 읽지 않아도 됩니다."
                  },
                  {
                    role: "운영 담당자",
                    rating: "★★★★★",
                    quote:
                      "권한이 있는 회의만 볼 수 있어 프로젝트 문서를 공유할 때도 부담이 적었습니다."
                  }
                ].map((review, index) => (
                  <article key={`${review.role}-clone-${index}`} className="review-card">
                    <div className="review-avatar">{review.role.slice(0, 2)}</div>
                    <strong>{review.role}</strong>
                    <div className="review-rating">{review.rating}</div>
                    <p>{review.quote}</p>
                  </article>
                ))}
              </div>
            </div>
          </div>
        </section>
      </main>
    </>
  );
}
