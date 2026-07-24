export type TranscriptDictionaryTerm = {
  term: string;
  definition: string;
  status?: string;
};

export type TranscriptTermSelectHandler = (term: TranscriptDictionaryTerm) => void;

const TERM_ALIASES: Record<string, string[]> = {
  rag: ["레그"],
  stt: ["에스티티"],
  ai: ["에이아이"],
  jwt: ["제이더블유티"]
};

function matchesTerm(value: string, term: string) {
  const normalizedValue = value.trim().toLocaleLowerCase();
  const normalizedTerm = term.trim().toLocaleLowerCase();
  return normalizedValue === normalizedTerm || (TERM_ALIASES[normalizedTerm] ?? []).includes(normalizedValue);
}

export function highlightTranscriptTerms(
  text: string,
  terms: TranscriptDictionaryTerm[],
  onSelectTerm?: TranscriptTermSelectHandler
) {
  const activeTerms = terms.filter((term) => term.status === undefined || term.status === "ACTIVE").filter((term) => term.term.trim());
  if (!activeTerms.length || !text) return text;
  const alternatives = activeTerms.flatMap((term) => [term.term.trim(), ...(TERM_ALIASES[term.term.trim().toLocaleLowerCase()] ?? [])]);
  const escaped = alternatives
    .sort((left, right) => right.length - left.length)
    .map((term) => term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  const matcher = new RegExp(`(${escaped.join("|")})`, "giu");
  return text.split(matcher).map((part, index) => {
    const matched = activeTerms.find((term) => matchesTerm(part, term.term));
    return matched ? (
      <TranscriptTerm key={`${part}-${index}`} value={part} term={matched} onSelect={onSelectTerm} />
    ) : part;
  });
}

function TranscriptTerm({
  value,
  term,
  onSelect
}: {
  value: string;
  term: TranscriptDictionaryTerm;
  onSelect?: TranscriptTermSelectHandler;
}) {
  return (
    <span
      className="transcript-term"
      onClick={() => onSelect?.(term)}
      role="button"
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect?.(term);
        }
      }}
    >
      {value}
    </span>
  );
}
