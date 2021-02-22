package fi.espoo.evaka.messaging

import fi.espoo.evaka.shared.db.Database
import org.jdbi.v3.core.kotlin.mapTo
import java.util.UUID

fun Database.Read.getDaycareDailyNote(noteId: UUID): DaycareDailyNote? {
    return createQuery("SELECT * FROM daycare_daily_note WHERE id = :id")
        .bind("id", noteId)
        .mapTo<DaycareDailyNote>()
        .first()
}

fun Database.Read.getChildDaycareDailyNotes(childId: UUID): List<DaycareDailyNote> {
    return createQuery("SELECT * FROM daycare_daily_note WHERE child_id = :id")
        .bind("id", childId)
        .mapTo<DaycareDailyNote>()
        .list()
}

fun Database.Read.getGroupDaycareDailyNotes(groupId: UUID): List<DaycareDailyNote> {
    return createQuery("SELECT * FROM daycare_daily_note WHERE group_id = :id")
        .bind("id", groupId)
        .mapTo<DaycareDailyNote>()
        .list()
}

fun Database.Transaction.upsertDaycareDailyNote(note: DaycareDailyNote) {
    createUpdate(
        """
INSERT INTO daycare_daily_note (child_id, group_id, date, note, feeding_note, sleeping_note, reminders, reminder_note, modified_by)
VALUES(:childId, :groupId, :date, :note, :feedingNote, :sleepingNote, :reminders, :reminderNote, :modifiedBy)
ON CONFLICT(child_id, group_id, date)
    DO UPDATE SET 
        child_id = :childId, 
        group_id = :groupId, 
        date = :date, 
        note = :note, 
        feeding_note = :feedingNote, 
        sleeping_note = :sleepingNote,
        reminders = :reminders,
        reminder_note = :reminderNote,
        modified_by = :modifiedBy
        modified_at = now()
        """.trimIndent()
    )
        .bind("childId", note.childId)
        .bind("groupId", note.groupId)
        .bind("date", note.date)
        .bind("note", note.note)
        .bind("feedingNote", note.feedingNote)
        .bind("sleepingNote", note.sleepingNote)
        .bind("reminders", note.reminders)
        .bind("reminderNote", note.reminderNote)
        .bind("modifiedBy", note.modifiedBy)
        .execute()
}
