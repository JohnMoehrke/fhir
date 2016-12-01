package org.hl7.fhir.dstu3.validation;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.dstu3.context.IWorkerContext;
import org.hl7.fhir.dstu3.model.ElementDefinition;
import org.hl7.fhir.dstu3.model.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.dstu3.model.OperationOutcome.IssueType;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.hl7.fhir.dstu3.utils.FHIRPathEngine;
import org.hl7.fhir.utilities.Utilities;

public class ProfileValidator extends BaseValidator {

  IWorkerContext context;

  public void setContext(IWorkerContext context) {
    this.context = context;    
  }

  protected boolean rule(List<ValidationMessage> errors, IssueType type, String path, boolean b, String msg) {
    String rn = path.contains(".") ? path.substring(0, path.indexOf(".")) : path;
    return super.rule(errors, type, path, b, msg, "<a href=\""+(rn.toLowerCase())+".html\">"+rn+"</a>: "+Utilities.escapeXml(msg));
  }

  public List<ValidationMessage> validate(StructureDefinition profile, boolean forBuild) {
    List<ValidationMessage> errors = new ArrayList<ValidationMessage>();
    
    // first check: extensions must exist
    for (ElementDefinition ec : profile.getDifferential().getElement())
      checkExtensions(profile, errors, "differential", ec);
    rule(errors, IssueType.STRUCTURE, profile.getId(), profile.hasSnapshot(), "missing Snapshot at "+profile.getName()+"."+profile.getName());
    for (ElementDefinition ec : profile.getSnapshot().getElement()) 
      checkExtensions(profile, errors, "snapshot", ec);

    if (rule(errors, IssueType.STRUCTURE, profile.getId(), profile.hasSnapshot(), "A snapshot is required")) {
      for (ElementDefinition ed : profile.getSnapshot().getElement()) {
        checkExtensions(profile, errors, "snapshot", ed);
        for (ElementDefinitionConstraintComponent inv : ed.getConstraint()) {
          if (forBuild) {
            if (!inExemptList(inv.getKey())) {
              if (rule(errors, IssueType.BUSINESSRULE, profile.getId()+"::"+ed.getPath()+"::"+inv.getKey(), inv.hasExpression(), "The invariant has no FHIR Path expression ("+inv.getXpath()+")")) {
                try {
                  new FHIRPathEngine(context).check(null, profile.getType(), ed.getPath(), inv.getExpression()); // , inv.hasXpath() && inv.getXpath().startsWith("@value")
                } catch (Exception e) {
//                  rule(errors, IssueType.STRUCTURE, profile.getId()+"::"+ed.getPath()+"::"+inv.getId(), exprExt != null, e.getMessage());
                }
              } 
            }
          }
        }
      }
    }
    return errors;
  }

  // these are special cases
  private boolean inExemptList(String key) {
    return key.startsWith("txt-");
  }

  private void checkExtensions(StructureDefinition profile, List<ValidationMessage> errors, String kind, ElementDefinition ec) {
    if (!ec.getType().isEmpty() && "Extension".equals(ec.getType().get(0).getCode()) && ec.getType().get(0).hasProfile()) {
      String url = ec.getType().get(0).getProfile();
      StructureDefinition defn = context.fetchResource(StructureDefinition.class, url);
      rule(errors, IssueType.BUSINESSRULE, profile.getId(), defn != null, "Unable to find Extension '"+url+"' referenced at "+profile.getUrl()+" "+kind+" "+ec.getPath()+" ("+ec.getSliceName()+")");
    }
  }
  
}
