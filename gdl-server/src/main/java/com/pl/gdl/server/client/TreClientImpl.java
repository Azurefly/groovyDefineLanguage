package com.pl.gdl.server.client;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.drift.orchestrator.FederatedExecutionCoordinator;
import com.pl.gdl.ontology.registry.OntologyRegistry;
import com.pl.gdl.runtime.compiler.GdlCompiler;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagSerializer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TreClientImpl implements TreClient {
    private final OntologyRegistry ontologyRegistry = OntologyRegistry.getInstance();
    private final FederatedExecutionCoordinator coordinator = new FederatedExecutionCoordinator();
    private final GdlCompiler compiler = new GdlCompiler();
    private final Map<String, TaskResult> taskResults = new ConcurrentHashMap<>();

    @Override
    public RegisterRsp registerOntology(String gdl) {
        return ontologyRegistry.registerOntology(gdl);
    }

    @Override
    public RegisterRsp registerOntologies(List<String> gdls) {
        return ontologyRegistry.registerOntologies(gdls);
    }

    @Override
    public RegisterRsp updateOntologies(List<String> gdls) {
        return ontologyRegistry.updateOntologies(gdls);
    }

    @Override
    public RegisterRsp unregisterOntology(String names) {
        return ontologyRegistry.unregisterOntology(names);
    }

    @Override
    public List<OntoInfoRsp> getOntologies(String fullOntologyNames) {
        return getOntologies(fullOntologyNames, "local");
    }

    @Override
    public List<OntoInfoRsp> getOntologies(String fullOntologyNames, String areaCode) {
        return ontologyRegistry.getOntologies(fullOntologyNames, areaCode);
    }

    @Override
    public String startTask(String gdlScript, Map<String, Object> params) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskResult result = coordinator.executeFederated(gdlScript, "local", params);
        result.setTaskId(taskId);
        taskResults.put(taskId, result);
        return taskId;
    }

    @Override
    public TaskResult getTaskResult(String taskId) {
        if (taskId == null) return null;
        return taskResults.get(taskId);
    }

    @Override
    public String getTsmlToDag(String gdlScript) {
        DagGraph dag = compiler.parseToDag(gdlScript, Map.of());
        return DagSerializer.toJson(dag);
    }
}
