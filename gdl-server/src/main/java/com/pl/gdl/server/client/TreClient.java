package com.pl.gdl.server.client;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;

import java.util.List;
import java.util.Map;

public interface TreClient {
    RegisterRsp registerOntology(String gdl);
    RegisterRsp registerOntologies(List<String> gdls);
    RegisterRsp updateOntologies(List<String> gdls);
    RegisterRsp unregisterOntology(String names);

    List<OntoInfoRsp> getOntologies(String fullOntologyNames);
    List<OntoInfoRsp> getOntologies(String fullOntologyNames, String areaCode);

    String startTask(String gdlScript, Map<String, Object> params);
    TaskResult getTaskResult(String taskId);
    String getTsmlToDag(String gdlScript);
}
