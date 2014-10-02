package edu.jhu.hlt.concrete.stanford;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

public class ConcreteStanfordProperties {
    private String version;
    private String toolName;
    private Properties props;

    public ConcreteStanfordProperties() throws IOException {
        this.props = this.loadProperties();
        version = props.getProperty("concrete-stanford.version");
        toolName = props.getProperty("tool.name");
    }

    public Properties getProperties() {
        return props;
    }

    public boolean getAllowEmptyMentions() {
        return Boolean.parseBoolean(props.getProperty("allow.empty.mentions"));
    }

    public String getTokenizerToolName() {
        return props.getProperty("toolName.tokenizer");
    }
    public String getLemmatizerToolName() {
        return props.getProperty("toolName.lemma");
    }
    public String getPOSToolName() {
        return props.getProperty("toolName.pos");
    }
    public String getNERToolName() {
        return props.getProperty("toolName.ner");
    }
    public String getCParseToolName() {
        return props.getProperty("toolName.cparser");
    }
    public String getDParseToolName() {
        return props.getProperty("toolName.dparser");
    }
    public String getCorefToolName() {
        return props.getProperty("toolName.coref");
    }

    public String getVersion() {
        return version;
    }

    public String getToolName() {
        return toolName;
    }

    private Properties loadProperties() throws IOException {
        Properties props = new Properties();
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("concrete-stanford.properties");
        props.load(in);
        in.close();
        return props;
    }
}
