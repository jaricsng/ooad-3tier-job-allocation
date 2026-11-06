package edu.ooad.joballoc.persistence.orm;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.Priority;
import edu.ooad.joballoc.persistence.dao.DataAccessException;
import edu.ooad.joballoc.persistence.dao.JobDAO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads demo jobs for today and tomorrow, so the board is never empty in class.
 *
 * <p>Before: a PostgreSQL-only SQL script ({@code to_char}, {@code cross join lateral},
 * {@code on conflict}). After: the jobs are data ({@code demo/demo-jobs.csv}) saved through the
 * {@link JobDAO}, so the same code works on every supported database. Each job is inserted only if
 * its id is missing (before: nothing was loaded if today already had any job, including yesterday's
 * "tomorrow" job, which left today almost empty).
 */
public final class DemoData {

    private static final Logger LOG = Logger.getLogger(DemoData.class.getName());
    private static final String RESOURCE = "/demo/demo-jobs.csv";
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMdd");
    private static final int FIELDS = 7;

    private DemoData() { }

    /** One line of the CSV file. */
    private record Row(String number, String title, Set<String> skills, int effortHours, int offsetDays,
            int dueAfterDays, Priority priority) {

        Job toJob(LocalDate today) {
            LocalDate day = today.plusDays(offsetDays);
            return new Job("J-" + day.format(MONTH_DAY) + "-" + number, title, skills, effortHours, day,
                    day.plusDays(dueAfterDays), priority);
        }
    }

    /**
     * Inserts the demo jobs that do not exist yet; returns how many were added. Call it inside a
     * transaction of whichever storage is in use (relational or in-memory).
     */
    public static int loadMissing(JobDAO jobs, LocalDate today) {
        int added = 0;
        for (Row row : read()) {
            Job job = row.toJob(today);
            if (jobs.findById(job.getJobId()).isEmpty()) {
                jobs.save(job);
                added++;
            }
        }
        if (added > 0) {
            int count = added;
            LOG.info(() -> "Loaded " + count + " demo jobs for " + today);
        }
        return added;
    }

    private static List<Row> read() {
        try (InputStream in = DemoData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new DataAccessException("Missing resource " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<Row> rows = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    rows.add(parse(line));
                }
            }
            return rows;
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + RESOURCE, e);
        }
    }

    private static Row parse(String line) {
        String[] f = line.split(",", -1);
        if (f.length != FIELDS) {
            throw new DataAccessException(RESOURCE + ": expected " + FIELDS + " fields in: " + line);
        }
        Iterator<String> next = Arrays.stream(f).map(String::strip).iterator();
        // Read the columns in file order (see the header of demo-jobs.csv).
        return new Row(next.next(), next.next(), new LinkedHashSet<>(List.of(next.next().split("\\|"))),
                Integer.parseInt(next.next()), Integer.parseInt(next.next()), Integer.parseInt(next.next()),
                Priority.valueOf(next.next()));
    }
}
