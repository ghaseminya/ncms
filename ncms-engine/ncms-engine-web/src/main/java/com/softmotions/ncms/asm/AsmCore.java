package com.softmotions.ncms.asm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Assembly core instance.
 * This class is not thread safe for concurrent updating.
 *
 * @author Adamansky Anton (adamansky@gmail.com)
 */
@JsonRootName("core")
@XmlAccessorType(XmlAccessType.NONE)
public class AsmCore implements Serializable {

    @JsonProperty
    Long id;

    @JsonProperty
    String location;

    @JsonProperty
    String name;

    @JsonProperty
    String templateEngine;

    public AsmCore() {
    }

    public AsmCore(Long id) {
        this.id = id;
    }

    public AsmCore(String location) {
        this.location = location;
    }

    public AsmCore(String location, String name) {
        this.location = location;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplateEngine() {
        return templateEngine;
    }

    public void setTemplateEngine(String templateEngine) {
        this.templateEngine = templateEngine;
    }

    public AsmCore cloneDeep() {
        AsmCore core = new AsmCore();
        core.id = id;
        core.location = location;
        core.name = name;
        core.templateEngine = templateEngine;
        return core;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AsmCore asmCore = (AsmCore) o;
        return location.equals(asmCore.location);
    }

    public int hashCode() {
        return location.hashCode();
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder("AsmCore{");
        sb.append("id=").append(id);
        sb.append(", location='").append(location).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", templateEngine='").append(templateEngine).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
