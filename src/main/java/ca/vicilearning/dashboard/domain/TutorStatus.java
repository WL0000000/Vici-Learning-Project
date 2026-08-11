package ca.vicilearning.dashboard.domain;

/**
 * Active = currently has at least one assigned student (recomputed every sync from
 * RosterStudent.assignedTutor matches). Inactive = no current assignments, not actively being
 * matched with new students. Legacy = no longer active but kept on record rather than deleted —
 * set automatically when a tutor disappears from SimplyBook.me, or manually by staff; the sync
 * never overwrites an existing Legacy status back to Active/Inactive.
 */
public enum TutorStatus {
    ACTIVE, INACTIVE, LEGACY
}
