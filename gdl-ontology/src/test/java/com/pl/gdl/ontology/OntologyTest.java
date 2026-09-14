package com.pl.gdl.ontology;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.ontology.ddl.OntologyDdlGenerator;
import com.pl.gdl.ontology.registry.OntologyMetadata;
import com.pl.gdl.ontology.registry.OntologyRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OntologyTest {

    @Test
    public void testOntologyRegistrationAndDdl() throws Exception {
        String gdl = """
            package v1
            import com.pl.gdl.ontology.model.Ontology
            import com.pl.gdl.ontology.annotation.Table
            import com.pl.gdl.ontology.annotation.Column

            @Table(type="ORC", remarks="群聊")
            class Chat extends Ontology {
                @Column(remarks="群标识")
                String groupId = "group_id"

                @Column(remarks="微信标识")
                String wxId = "wx_id"

                @Column(remarks="聊天内容")
                String content = "content"

                Chat() {
                    this.oId = "TS_001"
                    this.oName = "群聊本体"
                    this.oDesc = "群聊测试本体"
                    this.oAuthor = "A001"
                    this.oTable = "t_chat"
                }
            }
        """;

        OntologyRegistry registry = OntologyRegistry.getInstance();
        RegisterRsp rsp = registry.registerOntology(gdl);
        assertThat(rsp.getStatus()).isEqualTo(RegisterRsp.STATUS_SUCCESS);

        List<OntoInfoRsp> ontos = registry.getOntologies("v1.Chat", "local");
        assertThat(ontos).hasSize(1);
        OntoInfoRsp chatInfo = ontos.get(0);
        assertThat(chatInfo.getClassName()).isEqualTo("Chat");
        assertThat(chatInfo.getFields()).hasSize(3);

        Class<?> clazz = registry.getClassLoader().loadClass("v1.Chat", false, true);
        @SuppressWarnings("unchecked")
        OntologyMetadata meta = new OntologyMetadata((Class<? extends com.pl.gdl.ontology.model.Ontology>) clazz);
        String ddl = OntologyDdlGenerator.generateHiveDdl(meta);

        assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS chat");
        assertThat(ddl).contains("STORED AS ORC");
    }
}
