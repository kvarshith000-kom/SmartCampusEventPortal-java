/*
 * Smart Campus Event Portal - single-file console application (pure Java, Java 8+).
 *
 * Compile: javac SmartCampusEventPortal.java
 * Run    : java SmartCampusEventPortal
 *
 * Data is saved to portal_data.ser in the current folder and reloaded on startup.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.InputMismatchException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// ============================================================================ InvalidMobileNumberException
/**
 * Custom runtime exception used by the Smart Campus Event Portal.
 */
class InvalidMobileNumberException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidMobileNumberException(String message) {
        super(message);
    }
}

// ============================================================================ EventFullException
/**
 * Custom runtime exception used by the Smart Campus Event Portal.
 */
class EventFullException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EventFullException(String message) {
        super(message);
    }
}

// ============================================================================ AlreadyRegisteredException
/**
 * Custom runtime exception used by the Smart Campus Event Portal.
 */
class AlreadyRegisteredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AlreadyRegisteredException(String message) {
        super(message);
    }
}

// ============================================================================ InvalidTeamSizeException
/**
 * Custom runtime exception used by the Smart Campus Event Portal.
 */
class InvalidTeamSizeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidTeamSizeException(String message) {
        super(message);
    }
}

// ============================================================================ NotEnoughSlotsException
/**
 * Custom runtime exception used by the Smart Campus Event Portal.
 */
class NotEnoughSlotsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotEnoughSlotsException(String message) {
        super(message);
    }
}

// ============================================================================ EventType
/** Secondary event type. */
enum EventType {
    CULTURAL("Cultural"),
    HACKATHON("Hackathon"),
    OTHER("Other");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

// ============================================================================ Event
/**
 * Abstract base class for every campus event.
 * Concrete behaviour is supplied by {@link TechnicalEvent} and {@link NonTechnicalEvent}.
 */
abstract class Event implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int MIN_TEAM_SIZE = 1;

    private final int eventId;
    private String name;
    private String description;
    private String venue;
    private String date;
    private String time;
    private EventType eventType;
    private double registrationPrice;
    private int maxTeamSize;
    private int maxRegistrations;
    private int currentRegistrations;
    private boolean activityPoints;
    private boolean attendance;
    private String organizerMobile;
    private final String adminRollNo;

    private final List<Team> registeredTeams = new ArrayList<>();
    private final Set<String> participants = new LinkedHashSet<>();

    protected Event(int eventId, String name, String description, String venue, String date, String time,
                    EventType eventType, double registrationPrice, int maxTeamSize, int maxRegistrations,
                    boolean activityPoints, boolean attendance, String organizerMobile, String adminRollNo) {
        this.eventId = eventId;
        this.name = name;
        this.description = description;
        this.venue = venue;
        this.date = date;
        this.time = time;
        this.eventType = eventType;
        this.registrationPrice = registrationPrice;
        this.maxTeamSize = maxTeamSize;
        this.maxRegistrations = maxRegistrations;
        this.currentRegistrations = 0;
        this.activityPoints = activityPoints;
        this.attendance = attendance;
        this.organizerMobile = organizerMobile;
        this.adminRollNo = adminRollNo;
    }

    /** Primary classification label, e.g. "Technical" or "Non-Technical". */
    public abstract String getClassification();

    /** Short category-specific note shown in the detail view. */
    protected abstract String getCategoryNote();

    // ------------------------------------------------------------------ registration logic

    public boolean isTeamEvent() {
        return maxTeamSize > 1;
    }

    public int getAvailableSlots() {
        return Math.max(0, maxRegistrations - currentRegistrations);
    }

    public boolean isFull() {
        return currentRegistrations >= maxRegistrations;
    }

    public boolean isRegistered(String rollNo) {
        return participants.contains(normalize(rollNo));
    }

    public void ensureNotFull() {
        if (isFull()) {
            throw new EventFullException("Event '" + name + "' is full (" + currentRegistrations + "/"
                    + maxRegistrations + " registrations).");
        }
    }

    public void ensureNotRegistered(String rollNo) {
        if (isRegistered(rollNo)) {
            throw new AlreadyRegisteredException("Roll number " + normalize(rollNo)
                    + " is already registered for '" + name + "'.");
        }
    }

    public void validateTeamSize(int teamSize) {
        if (teamSize < MIN_TEAM_SIZE || teamSize > maxTeamSize) {
            throw new InvalidTeamSizeException("Team size " + teamSize + " is invalid. Allowed range: "
                    + MIN_TEAM_SIZE + " to " + maxTeamSize + ".");
        }
        ensureNotFull();
        if (getAvailableSlots() < teamSize) {
            throw new NotEnoughSlotsException("Only " + getAvailableSlots() + " slot(s) left, but your team needs "
                    + teamSize + ".");
        }
    }

    /** Registers a single student for a solo event (maxTeamSize == 1). */
    public void registerSolo(String rollNo) {
        ensureNotFull();
        ensureNotRegistered(rollNo);
        participants.add(normalize(rollNo));
        currentRegistrations += 1;
    }

    /**
     * Atomically registers a whole team. Every rule is validated before any state changes,
     * so a failure leaves the event untouched.
     */
    public void registerTeam(Team team) {
        validateTeamSize(team.getTeamSize());
        Set<String> seen = new LinkedHashSet<>();
        for (String roll : team.getAllRollNumbers()) {
            String r = normalize(roll);
            ensureNotRegistered(r);
            if (!seen.add(r)) {
                throw new AlreadyRegisteredException("Roll number " + r + " appears more than once in the team.");
            }
        }
        if (seen.size() != team.getTeamSize()) {
            throw new InvalidTeamSizeException("Team size " + team.getTeamSize() + " does not match "
                    + seen.size() + " member roll number(s).");
        }
        registeredTeams.add(team);
        participants.addAll(seen);
        currentRegistrations += team.getTeamSize();
    }

    public static String normalize(String rollNo) {
        return rollNo == null ? "" : rollNo.trim().toUpperCase();
    }

    // ------------------------------------------------------------------ display

    public String summaryLine() {
        return String.format("[%d] %-28s | %-13s | %-9s | %s %s | Slots %d/%d%s",
                eventId, truncate(name, 28), getClassification(), eventType.getLabel(), date, time,
                currentRegistrations, maxRegistrations, isFull() ? " (FULL)" : "");
    }

    /** Detailed view; subclasses override and call super to extend it. */
    public void displayDetails() {
        System.out.println("------------------------------------------------------------");
        System.out.println(" Event ID            : " + eventId);
        System.out.println(" Name                : " + name);
        System.out.println(" Classification      : " + getClassification());
        System.out.println(" Event Type          : " + eventType.getLabel());
        System.out.println(" Description         : " + description);
        System.out.println(" Venue               : " + venue);
        System.out.println(" Date / Time         : " + date + " at " + time);
        System.out.println(" Registration Price  : " + (registrationPrice == 0 ? "Free"
                : String.format("Rs. %.2f", registrationPrice)));
        System.out.println(" Participation       : " + (isTeamEvent() ? "Team (up to " + maxTeamSize + " members)" : "Solo"));
        System.out.println(" Slots               : " + currentRegistrations + " filled / " + maxRegistrations
                + " total (" + getAvailableSlots() + " available)");
        System.out.println(" Activity Points     : " + (activityPoints ? "Yes" : "No"));
        System.out.println(" Attendance Given    : " + (attendance ? "Yes" : "No"));
        System.out.println(" Organizer Contact   : " + organizerMobile);
        System.out.println(" Note                : " + getCategoryNote());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ------------------------------------------------------------------ getters / setters

    public int getEventId() { return eventId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public double getRegistrationPrice() { return registrationPrice; }
    public void setRegistrationPrice(double registrationPrice) { this.registrationPrice = registrationPrice; }
    public int getMaxTeamSize() { return maxTeamSize; }
    public void setMaxTeamSize(int maxTeamSize) { this.maxTeamSize = maxTeamSize; }
    public int getMaxRegistrations() { return maxRegistrations; }
    public void setMaxRegistrations(int maxRegistrations) { this.maxRegistrations = maxRegistrations; }
    public int getCurrentRegistrations() { return currentRegistrations; }
    public boolean hasActivityPoints() { return activityPoints; }
    public void setActivityPoints(boolean activityPoints) { this.activityPoints = activityPoints; }
    public boolean hasAttendance() { return attendance; }
    public void setAttendance(boolean attendance) { this.attendance = attendance; }
    public String getOrganizerMobile() { return organizerMobile; }
    public void setOrganizerMobile(String organizerMobile) { this.organizerMobile = organizerMobile; }
    public String getAdminRollNo() { return adminRollNo; }
    public List<Team> getRegisteredTeams() { return Collections.unmodifiableList(registeredTeams); }
    public Set<String> getParticipants() { return Collections.unmodifiableSet(participants); }
}

// ============================================================================ TechnicalEvent
/** An event under the Technical classification (hackathons, coding contests, workshops...). */
class TechnicalEvent extends Event {
    private static final long serialVersionUID = 1L;

    public TechnicalEvent(int eventId, String name, String description, String venue, String date, String time,
                          EventType eventType, double registrationPrice, int maxTeamSize, int maxRegistrations,
                          boolean activityPoints, boolean attendance, String organizerMobile, String adminRollNo) {
        super(eventId, name, description, venue, date, time, eventType, registrationPrice, maxTeamSize,
                maxRegistrations, activityPoints, attendance, organizerMobile, adminRollNo);
    }

    @Override
    public String getClassification() {
        return "Technical";
    }

    @Override
    protected String getCategoryNote() {
        return "Bring your laptop and charger; technical problem statements are shared at the venue.";
    }

    @Override
    public void displayDetails() {
        System.out.println("                 << TECHNICAL EVENT >>");
        super.displayDetails();
        System.out.println("------------------------------------------------------------");
    }
}

// ============================================================================ NonTechnicalEvent
/** An event under the Non-Technical classification (cultural fests, quizzes, sports...). */
class NonTechnicalEvent extends Event {
    private static final long serialVersionUID = 1L;

    public NonTechnicalEvent(int eventId, String name, String description, String venue, String date, String time,
                             EventType eventType, double registrationPrice, int maxTeamSize, int maxRegistrations,
                             boolean activityPoints, boolean attendance, String organizerMobile, String adminRollNo) {
        super(eventId, name, description, venue, date, time, eventType, registrationPrice, maxTeamSize,
                maxRegistrations, activityPoints, attendance, organizerMobile, adminRollNo);
    }

    @Override
    public String getClassification() {
        return "Non-Technical";
    }

    @Override
    protected String getCategoryNote() {
        return "Report 15 minutes early; costumes/props (if any) are the participants' responsibility.";
    }

    @Override
    public void displayDetails() {
        System.out.println("               << NON-TECHNICAL EVENT >>");
        super.displayDetails();
        System.out.println("------------------------------------------------------------");
    }
}

// ============================================================================ Student
/** A student profile. */
class Student implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String rollNo;
    private String name;
    private String phoneNumber;

    public Student(String rollNo, String name, String phoneNumber) {
        this.rollNo = Event.normalize(rollNo);
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public String getRollNo() { return rollNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    @Override
    public String toString() {
        return name + " (" + rollNo + ", " + phoneNumber + ")";
    }
}

// ============================================================================ Team
/** A team registration: the lead plus the roll numbers of the other members. */
class Team implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String leadRollNo;
    private final int teamSize;
    private final List<String> memberRollNumbers;

    /**
     * @param leadRollNo        roll number of the team lead
     * @param teamSize          total team size including the lead
     * @param memberRollNumbers roll numbers of the other (teamSize - 1) members
     */
    public Team(String leadRollNo, int teamSize, List<String> memberRollNumbers) {
        this.leadRollNo = Event.normalize(leadRollNo);
        this.teamSize = teamSize;
        this.memberRollNumbers = new ArrayList<>();
        for (String r : memberRollNumbers) {
            this.memberRollNumbers.add(Event.normalize(r));
        }
    }

    public String getLeadRollNo() { return leadRollNo; }
    public int getTeamSize() { return teamSize; }
    public List<String> getMemberRollNumbers() { return Collections.unmodifiableList(memberRollNumbers); }

    /** Lead first, followed by the other members. */
    public List<String> getAllRollNumbers() {
        List<String> all = new ArrayList<>();
        all.add(leadRollNo);
        all.addAll(memberRollNumbers);
        return all;
    }

    public boolean contains(String rollNo) {
        return getAllRollNumbers().contains(Event.normalize(rollNo));
    }

    @Override
    public String toString() {
        return "Lead " + leadRollNo + " + " + memberRollNumbers + " (size " + teamSize + ")";
    }
}

// ============================================================================ InputHelper
/**
 * Centralised console input with a numeric input guard: every numeric prompt loops until a
 * valid number is parsed, catching {@link InputMismatchException} and clearing the bad tokens.
 */
final class InputHelper {
    private static final Scanner SCANNER = new Scanner(System.in);

    private InputHelper() {
    }

    // ------------------------------------------------------------------ numeric guard

    public static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = SCANNER.nextInt();
                SCANNER.nextLine(); // consume the rest of the line
                if (value < min || value > max) {
                    System.out.println("  ! Please enter a number between " + min + " and " + max + ".");
                    continue;
                }
                return value;
            } catch (InputMismatchException e) {
                String bad = SCANNER.nextLine(); // clear the invalid token(s)
                System.out.println("  ! \"" + bad.trim() + "\" is not a valid whole number. Try again.");
            }
        }
    }

    public static int readInt(String prompt, int min) {
        return readInt(prompt, min, Integer.MAX_VALUE);
    }

    public static double readDouble(String prompt, double min) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = SCANNER.nextDouble();
                SCANNER.nextLine();
                if (Double.isNaN(value) || Double.isInfinite(value) || value < min) {
                    System.out.println("  ! Please enter a number greater than or equal to " + min + ".");
                    continue;
                }
                return value;
            } catch (InputMismatchException e) {
                String bad = SCANNER.nextLine();
                System.out.println("  ! \"" + bad.trim() + "\" is not a valid number. Try again.");
            }
        }
    }

    // ------------------------------------------------------------------ text

    public static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = SCANNER.nextLine().trim();
            if (!line.isEmpty()) {
                return line;
            }
            System.out.println("  ! This field cannot be empty.");
        }
    }

    /** Used while editing: pressing Enter keeps the current value. */
    public static String readTextOrKeep(String label, String current) {
        System.out.print(label + " [" + current + "] (Enter to keep): ");
        String line = SCANNER.nextLine().trim();
        return line.isEmpty() ? current : line;
    }

    public static boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt + " (y/n): ");
            String line = SCANNER.nextLine().trim().toLowerCase();
            if (line.equals("y") || line.equals("yes")) {
                return true;
            }
            if (line.equals("n") || line.equals("no")) {
                return false;
            }
            System.out.println("  ! Please answer y or n.");
        }
    }

    public static void pause() {
        System.out.print("\nPress Enter to continue...");
        SCANNER.nextLine();
    }

    // ------------------------------------------------------------------ mobile numbers

    /** @throws InvalidMobileNumberException if the value is not exactly 10 digits */
    public static String validateMobile(String mobile) {
        String m = mobile == null ? "" : mobile.trim();
        if (!m.matches("\\d+")) {
            throw new InvalidMobileNumberException("Mobile number must contain digits only (got \"" + m + "\").");
        }
        if (m.length() != 10) {
            throw new InvalidMobileNumberException("Mobile number must be exactly 10 digits (got " + m.length() + ").");
        }
        return m;
    }

    public static String readMobile(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = SCANNER.nextLine();
            try {
                return validateMobile(line);
            } catch (InvalidMobileNumberException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }
}

// ============================================================================ PortalData
/** The complete application state that gets serialized to disk. */
class PortalData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Event> events = new ArrayList<>();
    private final Map<String, Student> students = new LinkedHashMap<>();
    private int nextEventId = 1;

    public List<Event> getEvents() { return events; }
    public Map<String, Student> getStudents() { return students; }

    public int nextEventId() {
        return nextEventId++;
    }
}

// ============================================================================ DataStore
/** Persists {@link PortalData} with Java object serialization. */
class DataStore {
    private final File file;

    public DataStore(String fileName) {
        this.file = new File(fileName);
    }

    public PortalData load() {
        if (!file.exists()) {
            System.out.println("[i] No saved data found. Starting with an empty portal.");
            return new PortalData();
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            PortalData data = (PortalData) in.readObject();
            System.out.println("[i] Loaded " + data.getEvents().size() + " event(s) and "
                    + data.getStudents().size() + " student profile(s) from " + file.getName() + ".");
            return data;
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.out.println("[!] Could not read " + file.getName() + " (" + e.getMessage()
                    + "). Starting with an empty portal.");
            return new PortalData();
        }
    }

    public void save(PortalData data) {
        // Write to a temp file first so a crash mid-write never corrupts the existing data file.
        File tmp = new File(file.getAbsolutePath() + ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tmp))) {
            out.writeObject(data);
        } catch (IOException e) {
            System.out.println("[!] Failed to save data: " + e.getMessage());
            return;
        }
        if (file.exists() && !file.delete()) {
            System.out.println("[!] Failed to replace " + file.getName());
            return;
        }
        if (!tmp.renameTo(file)) {
            System.out.println("[!] Failed to finalise " + file.getName());
        }
    }
}

// ============================================================================ EventService
/** Business operations over the portal data; saves automatically after every change. */
class EventService {
    private final DataStore store;
    private final PortalData data;

    public EventService(DataStore store) {
        this.store = store;
        this.data = store.load();
    }

    public int nextEventId() {
        return data.nextEventId();
    }

    public void addEvent(Event event) {
        data.getEvents().add(event);
        save();
    }

    public boolean deleteEvent(Event event) {
        boolean removed = data.getEvents().remove(event);
        save();
        return removed;
    }

    public List<Event> findEvents(Predicate<Event> filter) {
        return data.getEvents().stream()
                .filter(filter)
                .sorted(Comparator.comparingInt(Event::getEventId))
                .collect(Collectors.toList());
    }

    public List<Event> getEventsByAdmin(String adminRollNo) {
        String admin = Event.normalize(adminRollNo);
        return findEvents(e -> e.getAdminRollNo().equals(admin));
    }

    /** @param classification "Technical", "Non-Technical", or null for all events */
    public List<Event> getEventsByClassification(String classification) {
        return findEvents(e -> classification == null || e.getClassification().equalsIgnoreCase(classification));
    }

    public List<Event> getRegisteredEvents(String rollNo) {
        return findEvents(e -> e.isRegistered(rollNo));
    }

    public Optional<Event> findById(List<Event> pool, int id) {
        return pool.stream().filter(e -> e.getEventId() == id).findFirst();
    }

    public Optional<Student> findStudent(String rollNo) {
        return Optional.ofNullable(data.getStudents().get(Event.normalize(rollNo)));
    }

    public void saveStudent(Student student) {
        data.getStudents().put(student.getRollNo(), student);
        save();
    }

    public void registerSolo(Event event, String rollNo) {
        event.registerSolo(rollNo);
        save();
    }

    public void registerTeam(Event event, Team team) {
        event.registerTeam(team);
        save();
    }

    public String describeRoll(String rollNo) {
        return findStudent(rollNo).map(Student::toString).orElse(Event.normalize(rollNo) + " (no profile)");
    }

    public void save() {
        store.save(data);
    }
}

// ============================================================================ AdminConsole
/** Admin workflow: create events and edit only the events this admin owns. */
class AdminConsole {
    private final EventService service;
    private String adminRollNo;

    public AdminConsole(EventService service) {
        this.service = service;
    }

    public void start() {
        System.out.println("\n================ ADMIN LOGIN ================");
        adminRollNo = Event.normalize(InputHelper.readText("Enter your Admin Roll Number: "));
        System.out.println("Logged in as admin " + adminRollNo + ".");

        while (true) {
            System.out.println("\n================ ADMIN MENU (" + adminRollNo + ") ================");
            System.out.println(" 1. Create New Event");
            System.out.println(" 2. View & Edit My Created Events");
            System.out.println(" 0. Log Out");
            int choice = InputHelper.readInt("Choose an option: ", 0, 2);
            switch (choice) {
                case 1:
                    createEvent();
                    break;
                case 2:
                    viewAndEditMyEvents();
                    break;
                default:
                    System.out.println("Admin " + adminRollNo + " logged out.");
                    return;
            }
        }
    }

    // ------------------------------------------------------------------ create

    private void createEvent() {
        System.out.println("\n---------------- CREATE NEW EVENT ----------------");
        System.out.println("Primary classification:  1. Technical   2. Non-Technical");
        int classification = InputHelper.readInt("Choose classification: ", 1, 2);
        EventType type = readEventType();

        String name = InputHelper.readText("Event name        : ");
        String description = InputHelper.readText("Description       : ");
        String venue = InputHelper.readText("Venue             : ");
        String date = InputHelper.readText("Date (e.g. 15-12-2026): ");
        String time = InputHelper.readText("Time (e.g. 10:00 AM)  : ");
        double price = InputHelper.readDouble("Registration price (0 for free): ", 0);
        int maxTeamSize = InputHelper.readInt("Maximum team size (1 = solo event): ", 1);
        int maxRegistrations = InputHelper.readInt("Maximum allowed registrations (participants): ", maxTeamSize);
        boolean activityPoints = InputHelper.readYesNo("Are activity points available?");
        boolean attendance = InputHelper.readYesNo("Will attendance be given?");
        String mobile = InputHelper.readMobile("Organizer mobile number (10 digits): ");

        int id = service.nextEventId();
        // Dynamic method dispatch: the reference type is Event, the object is a concrete subclass.
        Event event = classification == 1
                ? new TechnicalEvent(id, name, description, venue, date, time, type, price, maxTeamSize,
                        maxRegistrations, activityPoints, attendance, mobile, adminRollNo)
                : new NonTechnicalEvent(id, name, description, venue, date, time, type, price, maxTeamSize,
                        maxRegistrations, activityPoints, attendance, mobile, adminRollNo);
        service.addEvent(event);

        System.out.println("\nEvent created successfully!");
        event.displayDetails();
    }

    private EventType readEventType() {
        System.out.println("Event type:  1. Cultural   2. Hackathon   3. Other");
        int t = InputHelper.readInt("Choose event type: ", 1, 3);
        return EventType.values()[t - 1];
    }

    // ------------------------------------------------------------------ view / edit

    private void viewAndEditMyEvents() {
        while (true) {
            List<Event> mine = service.getEventsByAdmin(adminRollNo);
            System.out.println("\n---------------- MY CREATED EVENTS ----------------");
            if (mine.isEmpty()) {
                System.out.println("You have not created any events yet.");
                return;
            }
            mine.forEach(e -> System.out.println(e.summaryLine()));
            int id = InputHelper.readInt("\nEnter an Event ID to manage (0 to go back): ", 0);
            if (id == 0) {
                return;
            }
            Optional<Event> selected = service.findById(mine, id);
            if (selected.isPresent()) {
                editEvent(selected.get());
            } else {
                System.out.println("  ! No event with ID " + id + " among your events.");
            }
        }
    }

    private void editEvent(Event event) {
        while (true) {
            event.displayDetails();
            System.out.println("\nEdit '" + event.getName() + "':");
            System.out.println("  1. Name              7. Registration price");
            System.out.println("  2. Description       8. Maximum team size");
            System.out.println("  3. Venue             9. Maximum registrations");
            System.out.println("  4. Date             10. Activity points (toggle)");
            System.out.println("  5. Time             11. Attendance (toggle)");
            System.out.println("  6. Event type       12. Organizer mobile number");
            System.out.println(" 13. View registrations");
            System.out.println(" 14. Delete this event");
            System.out.println("  0. Back");
            int choice = InputHelper.readInt("Choose an option: ", 0, 14);
            switch (choice) {
                case 0:
                    return;
                case 1:
                    event.setName(InputHelper.readTextOrKeep("Name", event.getName()));
                    break;
                case 2:
                    event.setDescription(InputHelper.readTextOrKeep("Description", event.getDescription()));
                    break;
                case 3:
                    event.setVenue(InputHelper.readTextOrKeep("Venue", event.getVenue()));
                    break;
                case 4:
                    event.setDate(InputHelper.readTextOrKeep("Date", event.getDate()));
                    break;
                case 5:
                    event.setTime(InputHelper.readTextOrKeep("Time", event.getTime()));
                    break;
                case 6:
                    event.setEventType(readEventType());
                    break;
                case 7:
                    event.setRegistrationPrice(InputHelper.readDouble("New registration price: ", 0));
                    break;
                case 8:
                    editMaxTeamSize(event);
                    break;
                case 9:
                    int min = Math.max(event.getCurrentRegistrations(), event.getMaxTeamSize());
                    event.setMaxRegistrations(InputHelper.readInt(
                            "New maximum registrations (at least " + min + "): ", min));
                    break;
                case 10:
                    event.setActivityPoints(!event.hasActivityPoints());
                    System.out.println("Activity points now: " + (event.hasActivityPoints() ? "Yes" : "No"));
                    break;
                case 11:
                    event.setAttendance(!event.hasAttendance());
                    System.out.println("Attendance now: " + (event.hasAttendance() ? "Yes" : "No"));
                    break;
                case 12:
                    event.setOrganizerMobile(InputHelper.readMobile("New organizer mobile (10 digits): "));
                    break;
                case 13:
                    showRegistrations(event);
                    InputHelper.pause();
                    continue;
                case 14:
                    if (InputHelper.readYesNo("Delete '" + event.getName() + "' and all its registrations?")) {
                        service.deleteEvent(event);
                        System.out.println("Event deleted.");
                        return;
                    }
                    continue;
                default:
                    continue;
            }
            service.save();
            System.out.println("Changes saved.");
        }
    }

    private void editMaxTeamSize(Event event) {
        if (event.getCurrentRegistrations() > 0) {
            System.out.println("  ! Team size cannot be changed once registrations have started.");
            return;
        }
        int size = InputHelper.readInt("New maximum team size (1 = solo, at most "
                + event.getMaxRegistrations() + "): ", 1, event.getMaxRegistrations());
        event.setMaxTeamSize(size);
    }

    private void showRegistrations(Event event) {
        System.out.println("\nRegistrations for '" + event.getName() + "' ("
                + event.getCurrentRegistrations() + "/" + event.getMaxRegistrations() + "):");
        if (event.getParticipants().isEmpty()) {
            System.out.println("  No registrations yet.");
            return;
        }
        if (event.isTeamEvent()) {
            int i = 1;
            for (Team team : event.getRegisteredTeams()) {
                System.out.println("  Team " + (i++) + " (size " + team.getTeamSize() + ")");
                System.out.println("    Lead   : " + service.describeRoll(team.getLeadRollNo()));
                team.getMemberRollNumbers().forEach(r -> System.out.println("    Member : " + service.describeRoll(r)));
            }
        } else {
            event.getParticipants().forEach(r -> System.out.println("  - " + service.describeRoll(r)));
        }
    }
}

// ============================================================================ StudentConsole
/** Student workflow: browse, filter, register (solo or team) and view the dashboard. */
class StudentConsole {
    private final EventService service;
    private Student student;

    public StudentConsole(EventService service) {
        this.service = service;
    }

    public void start() {
        login();
        while (true) {
            System.out.println("\n================ STUDENT MENU (" + student.getName() + ") ================");
            System.out.println(" 1. Browse & Register for Events");
            System.out.println(" 2. View My Registered Events");
            System.out.println(" 0. Log Out");
            int choice = InputHelper.readInt("Choose an option: ", 0, 2);
            switch (choice) {
                case 1:
                    browseEvents();
                    break;
                case 2:
                    showDashboard();
                    break;
                default:
                    System.out.println("Goodbye, " + student.getName() + "!");
                    return;
            }
        }
    }

    // ------------------------------------------------------------------ profile

    private void login() {
        System.out.println("\n================ STUDENT PROFILE ================");
        String roll = Event.normalize(InputHelper.readText("Roll Number : "));
        Optional<Student> existing = service.findStudent(roll);
        if (existing.isPresent() && InputHelper.readYesNo("Welcome back, " + existing.get().getName()
                + "! Use your saved profile " + existing.get() + "?")) {
            student = existing.get();
            return;
        }
        String name = InputHelper.readText("Name        : ");
        String phone = InputHelper.readMobile("Phone Number (10 digits): ");
        student = new Student(roll, name, phone);
        service.saveStudent(student);
        System.out.println("Profile saved: " + student);
    }

    // ------------------------------------------------------------------ browse

    private void browseEvents() {
        while (true) {
            System.out.println("\n---------------- BROWSE EVENTS ----------------");
            System.out.println(" 1. Technical Events");
            System.out.println(" 2. Non-Technical Events");
            System.out.println(" 3. All Events");
            System.out.println(" 0. Back");
            int choice = InputHelper.readInt("Filter by: ", 0, 3);
            if (choice == 0) {
                return;
            }
            String filter = choice == 1 ? "Technical" : choice == 2 ? "Non-Technical" : null;
            List<Event> events = service.getEventsByClassification(filter);
            if (events.isEmpty()) {
                System.out.println("No events found for this filter.");
                continue;
            }
            pickEvent(events);
        }
    }

    private void pickEvent(List<Event> events) {
        while (true) {
            System.out.println();
            events.forEach(e -> System.out.println(e.summaryLine()
                    + (e.isRegistered(student.getRollNo()) ? "  <-- registered" : "")));
            int id = InputHelper.readInt("\nEnter an Event ID to view details (0 to go back): ", 0);
            if (id == 0) {
                return;
            }
            Optional<Event> selected = service.findById(events, id);
            if (!selected.isPresent()) {
                System.out.println("  ! No event with ID " + id + " in this list.");
                continue;
            }
            Event event = selected.get();
            event.displayDetails(); // dynamic dispatch -> TechnicalEvent / NonTechnicalEvent
            if (event.isRegistered(student.getRollNo())) {
                System.out.println("You are already registered for this event.");
            } else if (event.isFull()) {
                System.out.println("This event is FULL.");
            } else if (InputHelper.readYesNo("Register for this event?")) {
                register(event);
            }
        }
    }

    // ------------------------------------------------------------------ registration

    private void register(Event event) {
        try {
            if (event.isTeamEvent()) {
                registerTeam(event);
            } else {
                service.registerSolo(event, student.getRollNo());
                System.out.println("Registered successfully for '" + event.getName() + "'!" + priceNote(event));
            }
        } catch (EventFullException | AlreadyRegisteredException
                | InvalidTeamSizeException | NotEnoughSlotsException e) {
            System.out.println("  ! Registration failed: " + e.getMessage());
        }
    }

    private void registerTeam(Event event) {
        String lead = student.getRollNo();
        event.ensureNotFull();
        event.ensureNotRegistered(lead);

        System.out.println("\nTeam registration - you (" + lead + ") are the Team Lead.");
        int teamSize;
        while (true) {
            teamSize = InputHelper.readInt("Total team size including you (" + Event.MIN_TEAM_SIZE + "-"
                    + event.getMaxTeamSize() + ", " + event.getAvailableSlots()
                    + " slot(s) left, 0 to cancel): ", Integer.MIN_VALUE);
            if (teamSize == 0) {
                System.out.println("Registration cancelled.");
                return;
            }
            try {
                event.validateTeamSize(teamSize);
                break;
            } catch (InvalidTeamSizeException | NotEnoughSlotsException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }

        List<String> members = new ArrayList<>();
        for (int i = 2; i <= teamSize; i++) {
            while (true) {
                String roll = Event.normalize(InputHelper.readText(
                        "Roll Number of member " + i + " of " + teamSize + " (0 to cancel): "));
                if (roll.equals("0")) {
                    System.out.println("Registration cancelled.");
                    return;
                }
                if (roll.equals(lead) || members.contains(roll)) {
                    System.out.println("  ! " + roll + " is already in this team. Enter a different roll number.");
                    continue;
                }
                try {
                    event.ensureNotRegistered(roll);
                } catch (AlreadyRegisteredException e) {
                    System.out.println("  ! " + e.getMessage());
                    continue;
                }
                members.add(roll);
                break;
            }
        }

        Team team = new Team(lead, teamSize, members);
        System.out.println("\nTeam: " + team);
        if (!InputHelper.readYesNo("Confirm team registration?")) {
            System.out.println("Registration cancelled.");
            return;
        }
        service.registerTeam(event, team); // atomic: validates everything again before committing
        System.out.println("Team registered successfully for '" + event.getName() + "'!" + priceNote(event));
    }

    private String priceNote(Event event) {
        return event.getRegistrationPrice() > 0
                ? String.format(" Registration fee: Rs. %.2f", event.getRegistrationPrice())
                : "";
    }

    // ------------------------------------------------------------------ dashboard

    private void showDashboard() {
        List<Event> mine = service.getRegisteredEvents(student.getRollNo());
        System.out.println("\n================ MY REGISTERED EVENTS ================");
        System.out.println("Student: " + student);
        if (mine.isEmpty()) {
            System.out.println("You have not registered for any events yet.");
            return;
        }
        mine.forEach(e -> {
            System.out.println(e.summaryLine());
            System.out.println("      Venue: " + e.getVenue() + " | Organizer: " + e.getOrganizerMobile()
                    + " | Activity points: " + (e.hasActivityPoints() ? "Yes" : "No")
                    + " | Attendance: " + (e.hasAttendance() ? "Yes" : "No"));
            e.getRegisteredTeams().stream()
                    .filter(t -> t.contains(student.getRollNo()))
                    .findFirst()
                    .ifPresent(t -> System.out.println("      Team: " + t));
        });
        long points = mine.stream().filter(Event::hasActivityPoints).count();
        System.out.println("\nTotal: " + mine.size() + " event(s), " + points + " with activity points.");
    }
}

// ============================================================================ Main
/** Entry point: the only public class in this file. */
public class SmartCampusEventPortal {
    private static final String DATA_FILE = "portal_data.ser";

    public static void main(String[] args) {
        System.out.println("=====================================================");
        System.out.println("          SMART CAMPUS EVENT PORTAL");
        System.out.println("=====================================================");

        EventService service = new EventService(new DataStore(DATA_FILE));
        try {
            while (true) {
                System.out.println("\n================ MAIN MENU ================");
                System.out.println(" 1. Admin Mode");
                System.out.println(" 2. Student Mode");
                System.out.println(" 0. Exit");
                int choice = InputHelper.readInt("Choose an option: ", 0, 2);
                if (choice == 1) {
                    new AdminConsole(service).start();
                } else if (choice == 2) {
                    new StudentConsole(service).start();
                } else {
                    break;
                }
            }
        } catch (NoSuchElementException e) {
            System.out.println("\n[i] Input closed.");
        } finally {
            service.save();
            System.out.println("All data saved to " + DATA_FILE + ". Goodbye!");
        }
    }
}
