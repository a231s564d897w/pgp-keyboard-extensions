## Custom Key Row, Unitto & Proton Pass overlays

### Custom Key Row
- Multi-row visual keyboard editor (Number / Q–P / A–L·Ñ / Z–M)
- Named presets: add, rename, delete, configure rows, export/import JSON
- SoftKey rebind for extra rows; language-aware final key (L vs Ñ)
- Access Points: apply active preset, or switch to a specific saved preset from the toolbar

### Unitto (required floating calculator)
- Access Point opens a floating window (not app-only)
- In-process Compose hub when `:unitto-compose` is packaged: Calculator, Converter (Length/Mass/Temp), Date
- **Insert into field** for calculator / converter / date results via InputConnection
- Fallback: official Unitto web app in WebView with **disk cache** (offline after first warm load)
- Title bar: App (native Unitto), Cache (clear web cache), Close
- Offline keypad remains last-resort fallback; expression state is cached

### Proton Pass–style credentials
- Floating credential list from Access Point (Samsung Pass–style overlay)
- Demo entries, local EntryStore persistence, Clear saved entries
- AutofillService skeleton + publishEntries bridge for future vault SDK
- Insert username/password into the focused field

### Tooling
- In-app debug panel with **Test** (self-check: engine + preset JSON)
- SYSTEM_ALERT_WINDOW permission infrastructure
- InputConnection bridge refreshed from SoftKey bind and calculator lifecycle hooks

### Packaging notes
- See `MERGE_GUIDE.md` and `GRADLE_INCLUDE_UNITTO_COMPOSE.md`
- Unitto is GPL-3.0; web fallback does not vendor Unitto source. Embedding upstream modules later requires GPL compliance.
