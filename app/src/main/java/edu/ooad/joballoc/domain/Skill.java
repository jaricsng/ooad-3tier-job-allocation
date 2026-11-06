package edu.ooad.joballoc.domain;

import edu.ooad.joballoc.persistence.mapping.Column;
import edu.ooad.joballoc.persistence.mapping.Entity;
import edu.ooad.joballoc.persistence.mapping.Id;
import edu.ooad.joballoc.persistence.mapping.Table;

/** Reference data: a skill a job can require and a worker can have. */
@Entity
@Table("skill")
public class Skill {

    @Id @Column("code") private String code;
    @Column("name") private String name;

    protected Skill() { }

    public Skill(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
}
