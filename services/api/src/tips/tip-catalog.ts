import { CyclePhase, TipCategory } from '@prisma/client';

export interface CatalogTip {
  id: string;
  category: TipCategory;
  titleDe: string;
  bodyDe: string;
  callToActionDe: string;
  titleEn: string;
  bodyEn: string;
  callToActionEn: string;
  requiredSharedFields: string[];
  applicablePhase?: CyclePhase;
  priority: number;
}

type TipText = [
  category: TipCategory,
  titleDe: string,
  bodyDe: string,
  actionDe: string,
  titleEn: string,
  bodyEn: string,
  actionEn: string,
  required?: string[],
  phase?: CyclePhase,
];

const texts: TipText[] = [
  [TipCategory.COMMUNICATION, 'Erst fragen', 'Unterstützung passt besser, wenn die andere Person selbst sagen kann, was gerade hilfreich ist.', 'Frag: „Was würde dir gerade helfen?“', 'Ask first', 'Support works better when the other person can say what would be helpful right now.', 'Ask: “What would help you right now?”'],
  [TipCategory.COMMUNICATION, 'Offen zuhören', 'Eine Antwort muss nicht sofort gelöst werden. Zuhören kann bereits entlasten.', 'Hör zu, ohne direkt einen Ratschlag anzuschließen.', 'Listen openly', 'An answer does not always need an immediate solution. Listening can already help.', 'Listen without immediately adding advice.'],
  [TipCategory.COMMUNICATION, 'Wahl lassen', 'Nähe und Ruhe können an verschiedenen Tagen unterschiedlich gut tun.', 'Frag, ob gerade Nähe, Ruhe oder etwas anderes gewünscht ist.', 'Leave the choice open', 'Closeness and space can feel different from day to day.', 'Ask whether closeness, space, or something else is wanted.'],
  [TipCategory.COMMUNICATION, 'Klar nachfragen', 'Vermutungen über Bedürfnisse treffen nicht immer zu.', 'Stell eine kurze, konkrete Frage statt zu raten.', 'Check clearly', 'Assumptions about someone’s needs are not always right.', 'Ask one short, specific question instead of guessing.'],
  [TipCategory.COMMUNICATION, 'Antwort respektieren', 'Ein Nein ist eine vollständige Antwort und keine Zurückweisung deiner Unterstützung.', 'Akzeptiere die Antwort freundlich und ohne Nachhaken.', 'Respect the answer', 'No is a complete answer and does not reject your willingness to help.', 'Accept the answer kindly without pressing.'],

  [TipCategory.PRACTICAL_HELP, 'Konkrete Aufgabe', 'Praktische Hilfe ist am leichtesten anzunehmen, wenn sie konkret und überschaubar ist.', 'Übernimm heute eine klar erkennbare Haushaltsaufgabe.', 'One concrete task', 'Practical help is easiest to accept when it is specific and manageable.', 'Take care of one clearly visible household task today.'],
  [TipCategory.PRACTICAL_HELP, 'Kleine Entlastung', 'Auch eine kleine erledigte Sache kann mentalen Platz schaffen.', 'Räum etwas weg, das ohnehin erledigt werden muss.', 'A small relief', 'Finishing even one small task can create mental space.', 'Put away something that needs doing anyway.'],
  [TipCategory.PRACTICAL_HELP, 'Planung abnehmen', 'Organisationsarbeit ist oft unsichtbar und trotzdem anstrengend.', 'Übernimm eine anstehende Planung vollständig und sag kurz Bescheid.', 'Take over planning', 'Organisation is often invisible and still takes effort.', 'Own one upcoming planning task and give a brief update.'],
  [TipCategory.PRACTICAL_HELP, 'Ohne Aufheben helfen', 'Unterstützung muss nicht als besondere Leistung inszeniert werden.', 'Erledige eine hilfreiche Kleinigkeit, ohne Lob dafür zu erwarten.', 'Help without fanfare', 'Support does not need to be presented as a special achievement.', 'Do one helpful thing without expecting praise.'],
  [TipCategory.PRACTICAL_HELP, 'Aufwand prüfen', 'Was für dich klein wirkt, kann für die andere Person heute viel Energie kosten.', 'Frag, welche eine Aufgabe du heute übernehmen kannst.', 'Check the load', 'Something that feels small to you may take a lot of energy for the other person today.', 'Ask which one task you can take over today.'],

  [TipCategory.COMFORT, 'Wärme anbieten', 'Wärme kann angenehm sein, aber nicht jede Person möchte sie jederzeit.', 'Frag kurz, ob eine Wärmflasche hilfreich wäre.', 'Offer warmth', 'Warmth can be comforting, but not everyone wants it every time.', 'Ask whether a hot-water bottle would help.'],
  [TipCategory.COMFORT, 'Bequem machen', 'Ein ruhiger, bequemer Platz kann eine kleine Entlastung sein.', 'Biete an, Kissen oder Decke bereitzulegen.', 'Make things comfortable', 'A quiet, comfortable place can provide a little relief.', 'Offer to bring a pillow or blanket.'],
  [TipCategory.COMFORT, 'Tempo reduzieren', 'Nicht jeder Moment braucht Produktivität oder ein volles Programm.', 'Frag, ob ihr einen Plan heute vereinfachen möchtet.', 'Slow the pace', 'Not every moment needs productivity or a full schedule.', 'Ask whether you should simplify a plan today.'],
  [TipCategory.COMFORT, 'Ruhige Atmosphäre', 'Weniger Reize können manchmal angenehm sein.', 'Biete an, Licht oder Lautstärke anzupassen.', 'A calmer setting', 'Fewer stimuli can sometimes feel better.', 'Offer to adjust the lights or volume.'],
  [TipCategory.COMFORT, 'Individuell bleiben', 'Was einmal geholfen hat, muss heute nicht automatisch passen.', 'Frag erneut, bevor du eine frühere Hilfe wiederholst.', 'Keep it individual', 'Something that helped once may not be right today.', 'Ask again before repeating a previous kind of help.'],

  [TipCategory.SPACE, 'Ruhe respektieren', 'Gewünschter Abstand ist kein persönlicher Angriff.', 'Gib Raum, ohne beleidigt oder demonstrativ zu reagieren.', 'Respect space', 'Wanting space is not a personal attack.', 'Give space without reacting resentfully or dramatically.'],
  [TipCategory.SPACE, 'Erreichbar bleiben', 'Raum geben und ansprechbar bleiben können gleichzeitig möglich sein.', 'Sag kurz, dass du erreichbar bist, und lass dann Ruhe.', 'Stay available', 'Giving space and remaining available can happen at the same time.', 'Say briefly that you are available, then allow quiet.'],
  [TipCategory.SPACE, 'Nicht ständig erinnern', 'Wiederholte Hinweise auf den Zyklus können sich beobachtend anfühlen.', 'Lass das Thema heute ruhen, sofern es nicht angesprochen wird.', 'Avoid constant reminders', 'Repeated cycle references can feel like being monitored.', 'Leave the topic alone today unless it is brought up.'],
  [TipCategory.SPACE, 'Eigene Zeit ermöglichen', 'Ungestörte Zeit kann helfen, ohne dass etwas erklärt werden muss.', 'Übernimm eine Verpflichtung, damit etwas freie Zeit entsteht.', 'Make room for personal time', 'Uninterrupted time can help without requiring an explanation.', 'Take over one commitment to create some free time.'],
  [TipCategory.SPACE, 'Signal vereinbaren', 'Eine einfache Absprache kann Missverständnisse bei Ruhebedarf vermeiden.', 'Frag, welches Zeichen künftig „Ich brauche kurz Ruhe“ bedeuten darf.', 'Agree on a signal', 'A simple agreement can prevent misunderstandings when space is needed.', 'Ask what signal could mean “I need some quiet” in future.'],

  [TipCategory.FOOD_AND_DRINK, 'Getränk mitbringen', 'Eine kleine Aufmerksamkeit darf unkompliziert bleiben.', 'Frag nach einem Getränk oder bring das bekannte Lieblingsgetränk mit.', 'Bring a drink', 'A small gesture can stay simple and low-key.', 'Ask about a drink or bring the familiar favourite.'],
  [TipCategory.FOOD_AND_DRINK, 'Auswahl anbieten', 'Appetit und Vorlieben können sich ändern; Vermutungen sind nicht nötig.', 'Biete zwei einfache Optionen an und akzeptiere auch „nichts“.', 'Offer a choice', 'Appetite and preferences can change; there is no need to guess.', 'Offer two simple options and accept “nothing” too.'],
  [TipCategory.FOOD_AND_DRINK, 'Etwas vorbereiten', 'Eine vorbereitete Kleinigkeit kann Wege und Entscheidungen sparen.', 'Frag, ob du einen Snack oder eine Mahlzeit vorbereiten sollst.', 'Prepare something', 'Having something prepared can save effort and decisions.', 'Ask whether you should prepare a snack or meal.'],
  [TipCategory.FOOD_AND_DRINK, 'Kein Kommentar nötig', 'Essen braucht keine Bewertung oder Erklärung.', 'Biete etwas an, ohne Menge oder Auswahl zu kommentieren.', 'No commentary needed', 'Food choices do not require judgement or explanation.', 'Offer something without commenting on the choice or amount.'],
  [TipCategory.FOOD_AND_DRINK, 'Gemeinsam entscheiden', 'Eine offene Frage kann zusätzliche Entscheidungsarbeit erzeugen.', 'Schlag zwei realistische Essensoptionen zur Auswahl vor.', 'Decide together', 'A very open question can create extra decision work.', 'Suggest two realistic meal options to choose from.'],

  [TipCategory.EMOTIONAL_SUPPORT, 'Ernst nehmen', 'Beschwerden und Gefühle verdienen Respekt, ohne bewertet oder relativiert zu werden.', 'Sag: „Ich nehme dich ernst.“', 'Take it seriously', 'Discomfort and feelings deserve respect without judgement or minimising.', 'Say: “I take you seriously.”'],
  [TipCategory.EMOTIONAL_SUPPORT, 'Nicht erklären', 'Unterstützung bedeutet nicht, die Erfahrung der anderen Person zu deuten.', 'Frag nach ihrer Sicht, statt eine Ursache zu behaupten.', 'Do not explain it away', 'Support does not mean interpreting the other person’s experience.', 'Ask about their view instead of claiming a cause.'],
  [TipCategory.EMOTIONAL_SUPPORT, 'Verlässlich sein', 'Kleine eingehaltene Zusagen schaffen mehr Sicherheit als große Versprechen.', 'Halte heute eine konkrete Zusage zuverlässig ein.', 'Be reliable', 'Small promises kept build more trust than grand promises.', 'Reliably keep one specific promise today.'],
  [TipCategory.EMOTIONAL_SUPPORT, 'Gefühle offenlassen', 'Ein Zyklustag sagt nicht automatisch voraus, wie jemand sich fühlt.', 'Frag neutral nach dem Befinden, ohne es dem Zyklus zuzuschreiben.', 'Leave feelings open', 'A cycle day does not automatically predict how someone feels.', 'Ask neutrally how they are without attributing it to the cycle.'],
  [TipCategory.EMOTIONAL_SUPPORT, 'Da sein', 'Manchmal ist Begleitung hilfreicher als eine Lösung.', 'Biete an zuzuhören und frag, ob eine Lösung überhaupt gewünscht ist.', 'Be present', 'Sometimes companionship is more useful than a solution.', 'Offer to listen and ask whether a solution is wanted at all.'],

  [TipCategory.PERIOD_DAYS, 'Bedarf erfragen', 'An tatsächlichen Periodentagen können Wünsche sehr unterschiedlich sein.', 'Frag, ob heute praktische Hilfe angenehm wäre.', 'Check what is needed', 'Needs can vary widely on actual period days.', 'Ask whether practical help would feel useful today.', ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [TipCategory.PERIOD_DAYS, 'Schmerzen nicht annehmen', 'Eine erfasste Periode verrät nicht automatisch etwas über Schmerzen.', 'Frag allgemein nach Unterstützung, ohne Beschwerden zu unterstellen.', 'Do not assume pain', 'A recorded period does not automatically reveal anything about pain.', 'Ask generally about support without assuming discomfort.', ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [TipCategory.PERIOD_DAYS, 'Pläne flexibel halten', 'Ein flexibler Plan lässt Raum, ohne Erwartungen vorzugeben.', 'Biete an, einen gemeinsamen Plan bei Bedarf anzupassen.', 'Keep plans flexible', 'A flexible plan makes room without setting expectations.', 'Offer to adjust a shared plan if needed.', ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [TipCategory.PERIOD_DAYS, 'Diskret unterstützen', 'Zyklusinformationen sind privat, auch wenn sie mit dir geteilt wurden.', 'Sprich das Thema nicht vor anderen Personen an.', 'Support discreetly', 'Cycle information remains private even when it is shared with you.', 'Do not bring it up around other people.', ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [TipCategory.PERIOD_DAYS, 'Selbstbestimmung achten', 'Geteilte Daten sind kein Auftrag, Entscheidungen für jemand anderen zu treffen.', 'Biete Hilfe an und lass die Entscheidung bei der anderen Person.', 'Protect autonomy', 'Shared data is not permission to make decisions for someone else.', 'Offer help and leave the decision with the other person.', ['sharePeriodDates'], CyclePhase.MENSTRUATION],

  [TipCategory.PREMENSTRUAL_ESTIMATE, 'Nur eine Schätzung', 'Der erwartete Zeitraum ist unsicher und sagt nichts Sicheres über das heutige Befinden.', 'Behandle die Prognose als grobe Planungshilfe, nicht als Erklärung.', 'Only an estimate', 'The expected window is uncertain and says nothing certain about how today feels.', 'Treat the forecast as rough planning help, not an explanation.', ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [TipCategory.PREMENSTRUAL_ESTIMATE, 'Vorausschauend planen', 'Eine kleine organisatorische Reserve kann angenehm sein, ohne Verhalten vorauszusagen.', 'Halte einen kommenden Termin nach Möglichkeit flexibel.', 'Plan with some room', 'A little planning margin can help without predicting anyone’s behaviour.', 'Keep an upcoming appointment flexible where possible.', ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [TipCategory.PREMENSTRUAL_ESTIMATE, 'Neutral nachfragen', 'Eine Prognose ist kein Grund, Stimmung oder Bedürfnisse zu unterstellen.', 'Frag wie an jedem anderen Tag offen nach dem Befinden.', 'Ask neutrally', 'A forecast is no reason to assume mood or needs.', 'Ask openly how things are, just as on any other day.', ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [TipCategory.PREMENSTRUAL_ESTIMATE, 'Privat behandeln', 'Ein geteilter Zeitraum bleibt vertrauliche Information.', 'Erwähne die Prognose nicht gegenüber Dritten.', 'Keep it private', 'A shared expected window remains confidential information.', 'Do not mention the forecast to anyone else.', ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [TipCategory.PREMENSTRUAL_ESTIMATE, 'Unsicherheit mitdenken', 'Breite Zeiträume spiegeln echte Schwankungen wider.', 'Plane nicht so, als wäre ein einzelner Tag sicher vorhergesagt.', 'Allow for uncertainty', 'A broad window reflects real variation.', 'Do not plan as though one exact day were guaranteed.', ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],

  [TipCategory.GENERAL, 'Kleine Geste', 'Aufmerksame Unterstützung muss nicht groß oder aufwendig sein.', 'Tu heute eine kleine freundliche Sache ohne Erwartung.', 'A small gesture', 'Thoughtful support does not need to be grand or elaborate.', 'Do one small kind thing today without expectation.'],
  [TipCategory.GENERAL, 'Partnerschaftlich handeln', 'Unterstützung funktioniert am besten auf Augenhöhe.', 'Frag, statt Entscheidungen für die andere Person zu treffen.', 'Act as partners', 'Support works best as an equal partnership.', 'Ask instead of making decisions for the other person.'],
  [TipCategory.GENERAL, 'Daten sind kein Etikett', 'Geteilte Zyklusdaten beschreiben einzelne Einträge, nicht die ganze Person.', 'Begegne der Person, nicht einer vermuteten Phase.', 'Data is not a label', 'Shared cycle data describes individual entries, not the whole person.', 'Respond to the person, not an assumed phase.'],
  [TipCategory.GENERAL, 'Dankbar für Vertrauen', 'Das Teilen privater Informationen ist freiwillig und kann jederzeit geändert werden.', 'Behandle alles Geteilte diskret und selbstverständlich.', 'Value the trust', 'Sharing private information is voluntary and can change at any time.', 'Treat everything shared discreetly and naturally.'],
  [TipCategory.GENERAL, 'Eigene Bedürfnisse benennen', 'Gute Begleitung schließt ehrliche, respektvolle Kommunikation auf beiden Seiten ein.', 'Sprich ein eigenes Bedürfnis ruhig aus, ohne Druck aufzubauen.', 'Name your own needs', 'Good support includes honest, respectful communication from both people.', 'State one need calmly without applying pressure.'],

  [TipCategory.COMMUNICATION, 'Nachfragen statt merken müssen', 'Niemand muss dauerhaft wissen, welche Unterstützung immer richtig ist.', 'Frag neu, auch wenn du eine frühere Antwort kennst.', 'Ask again', 'Nobody has to permanently know which support is always right.', 'Ask again even if you remember a previous answer.'],
  [TipCategory.PRACTICAL_HELP, 'Den ganzen Schritt übernehmen', 'Eine halb übernommene Aufgabe kann weiterhin Organisationsarbeit hinterlassen.', 'Erledige eine Aufgabe von Anfang bis Ende.', 'Own the whole step', 'A half-finished task can still leave organisational work behind.', 'Complete one task from start to finish.'],
  [TipCategory.COMFORT, 'Berührung nicht voraussetzen', 'Auch gut gemeinte Nähe sollte einvernehmlich sein.', 'Frag vor einer Umarmung oder Massage kurz nach.', 'Do not assume touch', 'Even well-meant closeness should be consensual.', 'Ask briefly before a hug or massage.'],
  [TipCategory.SPACE, 'Keine Rechtfertigung verlangen', 'Ein Wunsch nach Ruhe braucht keine ausführliche Begründung.', 'Akzeptiere den Wunsch ohne Verhör oder Diskussion.', 'No justification required', 'Wanting quiet does not require a detailed explanation.', 'Accept the request without interrogation or debate.'],
  [TipCategory.FOOD_AND_DRINK, 'Vorräte ergänzen', 'Bekannte Alltagsfavoriten verfügbar zu halten kann praktisch sein.', 'Frag, ob beim nächsten Einkauf etwas Bestimmtes mit soll.', 'Restock favourites', 'Keeping familiar everyday favourites available can be practical.', 'Ask whether anything specific should go on the next shopping list.'],
];

if (texts.length !== 50) {
  throw new Error(`The built-in tip catalogue must contain exactly 50 tips, found ${texts.length}`);
}

export const TIP_CATALOG: CatalogTip[] = texts.map((tip, index) => ({
  id: `10000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
  category: tip[0],
  titleDe: tip[1],
  bodyDe: tip[2],
  callToActionDe: tip[3],
  titleEn: tip[4],
  bodyEn: tip[5],
  callToActionEn: tip[6],
  requiredSharedFields: tip[7] ?? [],
  applicablePhase: tip[8],
  priority: 100 - index,
}));
