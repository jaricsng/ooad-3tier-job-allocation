package edu.ooad.joballoc.web.view;

import edu.ooad.joballoc.domain.Priority;
import org.junit.Test;

import static edu.ooad.joballoc.TestData.job;
import static org.junit.Assert.assertEquals;

/** The board states a job's skill requirement plainly, including "none". */
public class BoardViewTest {

    @Test
    public void listsRequiredSkills() {
        assertEquals("LINUX", BoardView.skillsOrAny(job("J1", 2, Priority.LOW, "LINUX")));
    }

    @Test
    public void saysWhenAJobRequiresNoSkill() {
        assertEquals(BoardView.NO_SKILL_NEEDED, BoardView.skillsOrAny(job("J1", 2, Priority.LOW)));
    }
}
