package edu.ooad.joballoc.persistence.dao;

import edu.ooad.joballoc.domain.Job;
import edu.ooad.joballoc.domain.JobCriteria;

import java.util.List;

/** DAO for {@link Job}. */
public interface JobDAO extends GenericDAO<Job, String> {
    /** Pending jobs for the criteria, highest priority first, then earliest due date. */
    List<Job> findPending(JobCriteria criteria);
}
