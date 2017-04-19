/*
 * Copyright 2017 Observational Health Data Sciences and Informatics [OHDSI.org].
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ohdsi.webapi.service;

import ar.com.fdvs.dj.core.DJConstants;
import ar.com.fdvs.dj.core.DynamicJasperHelper;
import ar.com.fdvs.dj.core.layout.ClassicLayoutManager;
import ar.com.fdvs.dj.domain.DJCalculation;
import ar.com.fdvs.dj.domain.DJCrosstab;
import ar.com.fdvs.dj.domain.DJDataSource;
import ar.com.fdvs.dj.domain.DynamicReport;
import ar.com.fdvs.dj.domain.Style;
import ar.com.fdvs.dj.domain.builders.ColumnBuilderException;
import ar.com.fdvs.dj.domain.builders.CrosstabBuilder;
import ar.com.fdvs.dj.domain.builders.DynamicReportBuilder;
import ar.com.fdvs.dj.domain.builders.FastReportBuilder;
import ar.com.fdvs.dj.domain.builders.StyleBuilder;
import ar.com.fdvs.dj.domain.builders.SubReportBuilder;
import ar.com.fdvs.dj.domain.constants.Border;
import ar.com.fdvs.dj.domain.constants.Font;
import ar.com.fdvs.dj.domain.constants.HorizontalAlign;
import ar.com.fdvs.dj.domain.constants.Stretching;
import ar.com.fdvs.dj.domain.constants.VerticalAlign;
import ar.com.fdvs.dj.util.SortUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.export.DocxReportConfiguration;
import net.sf.jasperreports.export.SimpleDocxExporterConfiguration;
import net.sf.jasperreports.export.SimpleDocxReportConfiguration;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.ohdsi.webapi.shiro.management.Security;
import org.ohdsi.webapi.study.Study;
import org.ohdsi.webapi.study.StudyCohort;
import org.ohdsi.webapi.study.StudySource;
import org.ohdsi.webapi.study.report.Report;
import org.ohdsi.webapi.study.report.ReportCohortPair;
import org.ohdsi.webapi.study.report.ReportContent;
import org.ohdsi.webapi.study.report.ReportCovariate;
import org.ohdsi.webapi.study.report.ReportRepository;
import org.ohdsi.webapi.study.report.ReportSource;
import org.ohdsi.webapi.study.report.ReportStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Chris Knoll <cknoll@ohdsi.org>
 */
@Path("/report")
@Component
public class StudyReportService extends AbstractDaoService {

  @Autowired
  StudyService studyService;

  @Autowired
  ReportRepository reportRepository;

  @Autowired
  private Security security;

  @PersistenceContext
  protected EntityManager entityManager;

  public static class ReportListItem {

    private Integer id;
    private String name;
    private String description;
    private Integer studyId;
    private String studyName;
    private String createdBy;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date createdDate;
    private String modifiedBy;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date modifiedDate;
    private ReportStatus status;

    public Integer getId() {
      return id;
    }

    public void setId(Integer id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public Integer getStudyId() {
      return studyId;
    }

    public void setStudyId(Integer studyId) {
      this.studyId = studyId;
    }

    public String getStudyName() {
      return studyName;
    }

    public void setStudyName(String studyName) {
      this.studyName = studyName;
    }

    public String getCreatedBy() {
      return createdBy;
    }

    public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
    }

    public Date getCreatedDate() {
      return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
      this.createdDate = createdDate;
    }

    public String getModifiedBy() {
      return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
      this.modifiedBy = modifiedBy;
    }

    public Date getModifiedDate() {
      return modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate) {
      this.modifiedDate = modifiedDate;
    }

    public ReportStatus getStatus() {
      return status;
    }

    public void setStatus(ReportStatus status) {
      this.status = status;
    }

  }

  public static class ReportDTO extends ReportListItem {

    public List<StudyService.CohortDetail> cohorts;
    public List<CohortPair> cohortPairs;
    public Set<ReportCovariate> covariates;
    public List<ReportContent> content;
    public List<ReportSourceDTO> sources;
  }

  public static class CohortPair {

    public int target;
    public int outcome;
    public boolean isActive;

    public CohortPair() {
    }

    public CohortPair(int target, int outcome, boolean isActive) {
      this.target = target;
      this.outcome = outcome;
      this.isActive = isActive;
    }
  }

  public static class ReportSourceDTO extends StudyService.StudySourceDTO {

    public boolean isActive;

    public ReportSourceDTO() {
    }

    public ReportSourceDTO(int sourceId, String name, boolean isActive) {
      this.sourceId = sourceId;
      this.name = name;
      this.isActive = isActive;
    }
  }
  
  public static class CovariateStat {
    private String dataSource;
    private String name;
    private double statValue;
    
    public CovariateStat () {}
    
    public CovariateStat (String dataSource, String name, double statValue) {
      this.dataSource = dataSource;
      this.name = name;
      this.statValue = statValue;
    }

    public String getDataSource() {
      return dataSource;
    }

    public void setDataSource(String dataSource) {
      this.dataSource = dataSource;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public double getStatValue() {
      return statValue;
    }

    public void setStatValue(double statValue) {
      this.statValue = statValue;
    }
  }

  private ReportDTO fromReport(Report report) {
    HashMap<Integer, StudyService.CohortDetail> cohorts = new HashMap<>();

    ReportDTO result = new ReportDTO();
    result.setId(report.getId());
    result.setName(report.getName());
    result.setDescription(report.getDescription());
    result.setStudyId(report.getStudy().getId());
    result.setStudyName(report.getStudy().getName());
    result.setCreatedBy(report.getCreatedBy());
    result.setCreatedDate(report.getCreatedDate());
    result.setModifiedBy(report.getModifiedBy());
    result.setModifiedDate(report.getModifiedDate());
    result.setStatus(report.getStatus());

    result.cohortPairs = report.getCohortPairs().stream().map(p -> {
      StudyService.CohortDetail target;
      if ((target = cohorts.get(p.getTarget().getId())) == null) {
        target = studyService.fromStudyCohort(p.getTarget());
        cohorts.put(target.cohortId, target);
      }

      StudyService.CohortDetail outcome;
      if ((outcome = cohorts.get(p.getOutcome().getId())) == null) {
        outcome = studyService.fromStudyCohort(p.getOutcome());
        cohorts.put(outcome.cohortId, outcome);
      }

      cohorts.put(target.cohortId, target);
      cohorts.put(outcome.cohortId, outcome);
      CohortPair pair = new CohortPair(target.cohortId, outcome.cohortId, p.isActive());
      return pair;
    }).collect(Collectors.toList());
    result.covariates = report.getCovariates().stream().collect(Collectors.toSet());
    result.cohorts = cohorts.values().stream().collect(Collectors.toList());
    result.content = report.getContent().stream().collect(Collectors.toList());
    result.sources = report.getSources().stream().map(s -> {
      return new ReportSourceDTO(s.getSource().getId(), s.getSource().getName(), s.isActive());
    }).collect(Collectors.toList());

    return result;
  }

  private ReportDTO save(ReportDTO report) {
    Date currentTime = Calendar.getInstance().getTime();
    Report reportEntity;

    if (report.getId() != null) {
      reportEntity = reportRepository.findOne(report.getId());
      reportEntity.setModifiedDate(currentTime);
      reportEntity.setModifiedBy(security.getSubject());
    } else {
      reportEntity = new Report();
      reportEntity.setCreatedDate(currentTime);
      reportEntity.setCreatedBy(security.getSubject());
      reportEntity.setModifiedBy(null);
      reportEntity.setModifiedDate(null);
      reportEntity.setStudy(entityManager.getReference(Study.class, report.getStudyId()));
    }

    reportEntity.setName(report.getName());
    reportEntity.setDescription(report.getDescription());
    reportEntity.setCohortPairs(report.cohortPairs.stream().map(p -> {
      ReportCohortPair pair = new ReportCohortPair();
      pair.setTarget(entityManager.getReference(StudyCohort.class, p.target));
      pair.setOutcome(entityManager.getReference(StudyCohort.class, p.outcome));
      pair.setActive(p.isActive);
      return pair;
    }).collect(Collectors.toList()));
    reportEntity.setContent(report.content);
    reportEntity.setCovariates(report.covariates);
    reportEntity.setSources(report.sources.stream().map(s -> {
      ReportSource source = new ReportSource();
      source.setActive(s.isActive);
      source.setSource(entityManager.getReference(StudySource.class, s.sourceId));
      return source;
    }).collect(Collectors.toList()));

    reportEntity = reportRepository.save(reportEntity);

    return fromReport(reportEntity);
  }

  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<ReportListItem> getReportList() {
    List<Report> reports = reportRepository.list();

    List<ReportListItem> result = reports.stream().map(r -> {
      ReportListItem item = new ReportListItem();
      item.setId(r.getId());
      item.setName(r.getName());
      item.setDescription(r.getDescription());
      item.setStudyId(r.getStudy().getId());
      item.setStudyName(r.getStudy().getName());
      item.setCreatedBy(r.getCreatedBy());
      item.setCreatedDate(r.getCreatedDate());
      item.setModifiedBy(r.getModifiedBy());
      item.setModifiedDate(r.getModifiedDate());
      item.setStatus(r.getStatus());

      return item;
    }).collect(Collectors.toList());

    return result;
  }

  @POST
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public ReportDTO createReport(ReportDTO report) {
    if (report.getId() != null) {
      // POST to url should result in a new creation of an entity, so clear any existing reportId.  
      // Alternatively we could throw an exception here.
      report.setId(null);
    }

    return save(report);
  }

  @PUT
  @Path("/{reportId}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public ReportDTO saveReport(ReportDTO report) {
    return save(report);
  }

  @GET
  @Path("/{reportId}")
  @Produces(MediaType.APPLICATION_JSON)
  @Transactional
  public ReportDTO getReport(
          @PathParam("reportId") final int reportId
  ) {
    Report report = reportRepository.findOne(reportId);
    return fromReport(report);
  }

  private List<CovariateStat> getDemographicsCovaraiteList() {
    List<CovariateStat> stats = new ArrayList<>();
    
    stats.add(new CovariateStat("CCAE","Covariate 1 very long text to see if it will wrap or not so we'll make this huge.", 0.75));
    stats.add(new CovariateStat("CCAE","Covariate 2", 0.24));
    stats.add(new CovariateStat("CCAE","Covariate 3", 0.11));
    stats.add(new CovariateStat("CCAE","Covariate 4", 0.25));
    stats.add(new CovariateStat("CCAE","Covariate 5", 0.61));
    stats.add(new CovariateStat("CCAE","Covariate 6", 0.22));

 //   stats.add(new CovariateStat("MDCR","Covariate 1", 0.55));
    stats.add(new CovariateStat("MDCR","Covariate 2", 0.55));
 //   stats.add(new CovariateStat("MDCR","Covariate 3", 0.55));
    stats.add(new CovariateStat("MDCR","Covariate 4", 0.22));
 //   stats.add(new CovariateStat("MDCR","Covariate 5", 0.55));
    stats.add(new CovariateStat("MDCR","Covariate 6", 0.11));

    stats.add(new CovariateStat("OPTUM","Covariate 1 very long text to see if it will wrap or not so we'll make this huge.", 0.22));
//    stats.add(new CovariateStat("OPTUM","Covariate 2", 0.55));
    stats.add(new CovariateStat("OPTUM","Covariate 3", 0.44));
//    stats.add(new CovariateStat("OPTUM","Covariate 4", 0.55));
    stats.add(new CovariateStat("OPTUM","Covariate 5", 0.66));
//    stats.add(new CovariateStat("OPTUM","Covariate 6", 0.55));

    stats.add(new CovariateStat("CCAE_v1","Covariate 1 very long text to see if it will wrap or not so we'll make this huge.", 0.75));
    stats.add(new CovariateStat("CCAE_v1","Covariate 2", 0.24));
    stats.add(new CovariateStat("CCAE_v1","Covariate 3", 0.11));
    stats.add(new CovariateStat("CCAE_v1","Covariate 4", 0.25));
    stats.add(new CovariateStat("CCAE_v1","Covariate 5", 0.61));
    stats.add(new CovariateStat("CCAE_v1","Covariate 6", 0.22));

 //   stats.add(new CovariateStat("MDCR_v1","Covariate 1", 0.55));
    stats.add(new CovariateStat("MDCR_v1","Covariate 2", 0.55));
 //   stats.add(new CovariateStat("MDCR_v1","Covariate 3", 0.55));
    stats.add(new CovariateStat("MDCR_v1","Covariate 4", 0.22));
 //   stats.add(new CovariateStat("MDCR_v1","Covariate 5", 0.55));
    stats.add(new CovariateStat("MDCR_v1","Covariate 6", 0.11));

    stats.add(new CovariateStat("OPTUM_v1","Covariate 1 very long text to see if it will wrap or not so we'll make this huge.", 0.22));
//    stats.add(new CovariateStat("OPTUM_v1","Covariate 2", 0.55));
    stats.add(new CovariateStat("OPTUM_v1","Covariate 3", 0.44));
//    stats.add(new CovariateStat("OPTUM_v1","Covariate 4", 0.55));
    stats.add(new CovariateStat("OPTUM_v1","Covariate 5", 0.66));
//    stats.add(new CovariateStat("OPTUM_v1","Covariate 6", 0.55));

    
    return stats;
  }
  private DynamicReport getReportListReport() throws ColumnBuilderException, ClassNotFoundException {
    FastReportBuilder drb = new FastReportBuilder();
    DynamicReport dr = drb.addColumn("ID", "id", Integer.class.getName(), 30, true)
            .addColumn("Report Name", "name", String.class.getName(), 50)
            .addColumn("Report Description", "description", String.class.getName(), 50)
            .addColumn("Created Date", "createdDate", Date.class.getName(), 30)
            .setTitle("Report List")
            .setPrintBackgroundOnOddRows(true)
            .setUseFullPageWidth(true)
            .build();
    return dr;
  }
  
  private DJCrosstab getCovariateCrossTab() throws Exception {
    
    Style measureStyle = new StyleBuilder(true).setPattern("#0.00%")
            .setHorizontalAlign(HorizontalAlign.RIGHT)
            .setVerticalAlign(VerticalAlign.TOP)
            .setFont(Font.ARIAL_MEDIUM)
            .setName("measureStyle")
            .build();

    Style columnStyle = new StyleBuilder(true)
            .setHorizontalAlign(HorizontalAlign.CENTER)
            .setVerticalAlign(VerticalAlign.MIDDLE)
            .setFont(Font.ARIAL_MEDIUM)
            .setName("crosstabColumn")
            .build();
    
    DJCrosstab djcross = new CrosstabBuilder()
            .setUseFullWidth(true)
            // .setAutomaticTitle(true)
            .setCellBorder(Border.PEN_1_POINT())
            .addColumn("Data Source", "dataSource", String.class.getName(), false, columnStyle, columnStyle, columnStyle)
            .addRow("Name", "name", String.class.getName(), false)
            .addMeasure("statValue", Double.class.getName(), DJCalculation.NOTHING, "Value", measureStyle)
            .setCellDimension(34,60)
            .setColumnHeaderHeight(30)
            .setRowHeaderWidth(220)
            .build();
    
    return djcross;
  }

  private JasperPrint getMainReport() throws Exception {

    Map<String, Object> params = new HashMap<>();

    // Define styles
    Style titleStyle = new Style("titleStyle");
    titleStyle.setFont(new Font(24, Font._FONT_VERDANA, true));

    // create container report to host embedded report sections
    
    DynamicReportBuilder drb = new DynamicReportBuilder();
    Integer margin = 20;
    drb.setTitleStyle(titleStyle)
            .setTitle("Concatenated reports") //defines the title of the report

            .setSubtitle("All the reports shown here are concatenated as sub reports")
            .setDetailHeight(15)
            .setLeftMargin(margin)
            .setRightMargin(margin)
            .setTopMargin(margin)
            .setBottomMargin(margin)
            .setUseFullPageWidth(true)
            .setWhenNoDataAllSectionNoDetail();
//            .addAutoText(AutoText.AUTOTEXT_PAGE_X_OF_Y, AutoText.POSITION_FOOTER, AutoText.ALIGNMENT_CENTER)
    drb.addConcatenatedReport(getReportListReport(),
            new ClassicLayoutManager(),
            "reportList",
            DJConstants.DATA_SOURCE_ORIGIN_PARAMETER,
            DJConstants.DATA_SOURCE_TYPE_COLLECTION,
            false);
    params.put("reportList", getReportList());

    // add crosstab report
    DJCrosstab demographicsCrossTab = getCovariateCrossTab();
    demographicsCrossTab.setDatasource(new DJDataSource("demographicsDS", DJConstants.DATA_SOURCE_ORIGIN_PARAMETER, DJConstants.DATA_SOURCE_TYPE_COLLECTION));
    DynamicReport demoCrossTabReport = new DynamicReportBuilder()
            .addHeaderCrosstab(demographicsCrossTab)
            .setUseFullPageWidth(true)
            .build();
    drb.addConcatenatedReport(demoCrossTabReport,
            new ClassicLayoutManager(),
            "demographicsDS",
            DJConstants.DATA_SOURCE_ORIGIN_PARAMETER,
            DJConstants.DATA_SOURCE_TYPE_COLLECTION,
            false);
    params.put("demographicsDS", SortUtils.sortCollection(getDemographicsCovaraiteList(), demographicsCrossTab));

    drb.addConcatenatedReport(getReportListReport(),
            new ClassicLayoutManager(),
            "reportList",
            DJConstants.DATA_SOURCE_ORIGIN_PARAMETER,
            DJConstants.DATA_SOURCE_TYPE_COLLECTION,
            false);
    params.put("reportList", getReportList());

    // fill report
    JasperPrint jp = DynamicJasperHelper.generateJasperPrint(drb.build(), new ClassicLayoutManager(), params);
    return jp;
  }

  @GET
  @Path("/{reportId}.pdf")
  public Response getSamplePDF() throws Exception {

    JasperPrint jp = getMainReport();

    StreamingOutput output = (out) -> {
      try {
        JasperExportManager.exportReportToPdfStream(jp, out);
      } catch (JRException ex) {
        throw new WebApplicationException(ex, Response.Status.INTERNAL_SERVER_ERROR);
      }
    };

    Response response = Response
            .ok(output)
            .type("application/pdf")
            .header("Content-disposition", "inline; filename=reportList.pdf")
            .build();

    return response;
  }

  @GET
  @Path("/{reportId}.docx")
  public Response getSampleDocx() throws Exception {

    JasperPrint jp = getMainReport();

    // stream output to client
    StreamingOutput output = (out) -> {
      JRDocxExporter exporter = new JRDocxExporter();
      
      SimpleDocxReportConfiguration config = new SimpleDocxReportConfiguration();
      config.setFramesAsNestedTables(false);
      exporter.setConfiguration(config);
      
      exporter.setExporterInput(new SimpleExporterInput(jp));
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
      try {
        exporter.exportReport();
      } catch (JRException ex) {
        throw new WebApplicationException(ex, Response.Status.INTERNAL_SERVER_ERROR);
      }
    };

    Response response = Response
            .ok(output)
            .type("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .header("Content-disposition", "inline; filename=reportList.docx")
            .build();

    return response;
  }

}
