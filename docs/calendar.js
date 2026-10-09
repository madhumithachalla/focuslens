// Calendar export via the open iCalendar standard (.ics) plus a Google Calendar prefilled
// link — both work with zero accounts, zero API keys, and zero cost, and both work with
// any calendar app (Google Calendar, Apple Calendar, Outlook) on any device.

const DEFAULT_DURATION_MS = 30 * 60 * 1000;

function toIcsDate(ms) {
  return new Date(ms).toISOString().replace(/[-:]/g, "").split(".")[0] + "Z";
}

function escapeIcsText(s) {
  return String(s).replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\n/g, "\\n");
}

export function buildIcs(title, startMs, endMs = startMs + DEFAULT_DURATION_MS, description = "") {
  const uid = `${startMs}-${Math.random().toString(36).slice(2)}@focuslens`;
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//FocusLens//Web//EN",
    "BEGIN:VEVENT",
    `UID:${uid}`,
    `DTSTAMP:${toIcsDate(Date.now())}`,
    `DTSTART:${toIcsDate(startMs)}`,
    `DTEND:${toIcsDate(endMs)}`,
    `SUMMARY:${escapeIcsText(title)}`,
    description ? `DESCRIPTION:${escapeIcsText(description)}` : null,
    "END:VEVENT",
    "END:VCALENDAR",
  ].filter(Boolean);
  return lines.join("\r\n");
}

export function downloadIcs(filename, icsText) {
  const blob = new Blob([icsText], { type: "text/calendar;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export function googleCalendarUrl(title, startMs, endMs = startMs + DEFAULT_DURATION_MS, description = "") {
  const params = new URLSearchParams({
    action: "TEMPLATE",
    text: title,
    dates: `${toIcsDate(startMs)}/${toIcsDate(endMs)}`,
    details: description,
  });
  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}
