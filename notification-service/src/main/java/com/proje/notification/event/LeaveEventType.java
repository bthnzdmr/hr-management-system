package com.proje.notification.event;

// Ureticideki enum'un ikizi. Ortak bir modulde DEGIL: servisler arasindaki
// sozlesme JSON'dur, Java sinifi degil.
public enum LeaveEventType {

    REQUESTED,
    DECIDED,
    CANCELLED
}
