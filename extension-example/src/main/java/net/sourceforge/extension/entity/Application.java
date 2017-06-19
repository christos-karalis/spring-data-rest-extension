package net.sourceforge.extension.entity;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * Created by christos.karalis on 6/7/2017.
 */
@Entity
public class Application {

    @Id
    @Column(name = "APPLICATION_ID")
    private Long id;

    @Column(name = "SUBMISSION_DATE")
    private Date submissionDate;

    @Column
    private byte[] upload;

    @ManyToOne
    @JoinColumn(name = "APPLICANT_ID")
    private Applicant applicant;

    @OneToMany(mappedBy = "application")
    private List<OrderLine> orderLines;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(Date submissionDate) {
        this.submissionDate = submissionDate;
    }

    public byte[] getUpload() {
        return upload;
    }

    public void setUpload(byte[] upload) {
        this.upload = upload;
    }

    public Applicant getApplicant() {
        return applicant;
    }

    public void setApplicant(Applicant applicant) {
        this.applicant = applicant;
    }

    public List<OrderLine> getOrderLines() {
        return orderLines;
    }

    public void setOrderLines(List<OrderLine> orderLines) {
        this.orderLines = orderLines;
    }
}
