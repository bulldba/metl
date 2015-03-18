package org.jumpmind.symmetric.is.ui.views;

import java.util.List;
import java.util.Set;

import org.jumpmind.symmetric.is.core.model.Agent;
import org.jumpmind.symmetric.is.core.model.AgentDeployment;
import org.jumpmind.symmetric.is.core.model.AgentStartMode;
import org.jumpmind.symmetric.is.core.model.Component;
import org.jumpmind.symmetric.is.core.model.ComponentVersion;
import org.jumpmind.symmetric.is.core.model.FlowStep;
import org.jumpmind.symmetric.is.core.model.FlowStepLink;
import org.jumpmind.symmetric.is.core.model.FlowVersion;
import org.jumpmind.symmetric.is.core.persist.IConfigurationService;
import org.jumpmind.symmetric.is.core.runtime.IAgentManager;
import org.jumpmind.symmetric.is.ui.common.ApplicationContext;
import org.jumpmind.symmetric.is.ui.common.IBackgroundRefreshable;
import org.jumpmind.symmetric.is.ui.common.Icons;
import org.jumpmind.symmetric.is.ui.common.TabbedApplicationPanel;
import org.jumpmind.symmetric.is.ui.diagram.Diagram;
import org.jumpmind.symmetric.is.ui.diagram.Node;
import org.jumpmind.symmetric.is.ui.diagram.NodeMovedEvent;
import org.jumpmind.symmetric.is.ui.diagram.NodeSelectedEvent;
import org.jumpmind.symmetric.is.ui.diagram.ResourceEvent;
import org.jumpmind.symmetric.is.ui.views.manage.ExecutionLogPanel;
import org.jumpmind.symmetric.ui.common.IUiPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.DragAndDropWrapper.WrapperTargetDetails;
import com.vaadin.ui.DragAndDropWrapper.WrapperTransferable;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Panel;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.VerticalSplitPanel;
import com.vaadin.ui.themes.ValoTheme;

public class DesignFlowLayout extends HorizontalLayout implements IUiPanel, IBackgroundRefreshable {

    private static final long serialVersionUID = 1L;

    final Logger log = LoggerFactory.getLogger(getClass());

    ApplicationContext context;

    FlowVersion componentFlowVersion;

    DesignPropertySheet designPropertySheet;

    DesignNavigator designNavigator;

    DesignComponentPalette designComponentPalette;

    TabbedApplicationPanel tabs;

    Diagram diagram;

    CssLayout diagramLayout;

    Button runButton;

    public DesignFlowLayout(ApplicationContext context, FlowVersion componentFlowVersion,
            DesignNavigator designNavigator, TabbedApplicationPanel tabs) {
        this.context = context;
        this.tabs = tabs;
        this.componentFlowVersion = componentFlowVersion;
        this.designNavigator = designNavigator;

        this.designPropertySheet = new DesignPropertySheet(context);
        this.designComponentPalette = new DesignComponentPalette(this,
                context.getComponentFactory());

        addComponent(designComponentPalette);

        VerticalLayout rightLayout = new VerticalLayout();
        rightLayout.setSizeFull();

        rightLayout.addComponent(buildButtonBar());

        VerticalSplitPanel splitPanel = new VerticalSplitPanel();
        splitPanel.setSizeFull();
        splitPanel.setSplitPosition(50, Unit.PERCENTAGE);

        diagramLayout = new CssLayout();
        diagramLayout.setWidth(10000, Unit.PIXELS);
        diagramLayout.setHeight(10000, Unit.PIXELS);

        DragAndDropWrapper wrapper = new DragAndDropWrapper(diagramLayout);
        wrapper.setSizeUndefined();
        wrapper.setDropHandler(new DropHandler());

        Panel panel = new Panel();
        panel.setSizeFull();
        panel.addStyleName(ValoTheme.PANEL_WELL);
        panel.setContent(wrapper);

        splitPanel.addComponent(panel);
        splitPanel.addComponent(designPropertySheet);

        rightLayout.addComponent(splitPanel);
        rightLayout.setExpandRatio(splitPanel, 1);

        addComponent(rightLayout);
        setExpandRatio(rightLayout, 1);

        redrawFlow();

        context.getBackgroundRefresherService().register(this);
    }

    protected HorizontalLayout buildButtonBar() {
        HorizontalLayout layout = new HorizontalLayout();
        //layout.setMargin(true);
        layout.setSpacing(true);
        
        Button runButton = new Button("Run", Icons.RUN);
        runButton.addStyleName(ValoTheme.BUTTON_SMALL);
        runButton.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
        runButton.addStyleName(ValoTheme.BUTTON_BORDERLESS_COLORED);
        runButton.addClickListener(new ClickListener() {            
            private static final long serialVersionUID = 1L;
            @Override
            public void buttonClick(ClickEvent event) {
                openExecution();
            }
        });

        layout.addComponent(runButton);
        return layout;
    }

    @Override
    public void onBackgroundUIRefresh(Object backgroundData) {
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object onBackgroundDataRefresh() {
        return null;
    }

    @Override
    public boolean closing() {
        context.getBackgroundRefresherService().unregister(this);
        designNavigator.setDesignPropertySheet(designPropertySheet);
        return true;
    }

    @Override
    public void showing() {
        designNavigator.setDesignPropertySheet(designPropertySheet);
    }

    protected int countComponentsOfType(String type) {
        int count = 0;
        List<FlowStep> nodes = componentFlowVersion.getFlowSteps();
        for (FlowStep componentflowStep : nodes) {
            if (componentflowStep.getComponentVersion().getComponent().getType().equals(type)) {
                count++;
            }
        }
        return count;
    }

    protected void addComponent(int x, int y, Component component) {
        ComponentVersion componentVersion = new ComponentVersion(component);
        componentVersion.setVersionName("version 1.0");

        component.setName(component.getType() + " "
                + (countComponentsOfType(component.getType()) + 1));

        FlowStep flowStep = new FlowStep(componentVersion);
        flowStep.setX(x);
        flowStep.setY(y);
        flowStep.setFlowVersionId(componentFlowVersion.getId());
        componentFlowVersion.getFlowSteps().add(flowStep);

        context.getConfigurationService().save(flowStep);

        redrawFlow();

        designPropertySheet.valueChange(componentVersion);

        designNavigator.refresh();
        designNavigator.select(flowStep);

    }

    protected void redrawFlow() {
        if (diagram != null) {
            diagramLayout.removeComponent(diagram);
        }

        diagram = new Diagram();
        diagram.addListener(new DiagramChangedListener());

        diagramLayout.addComponent(diagram);

        List<FlowStepLink> links = componentFlowVersion.getFlowStepLinks();

        List<FlowStep> flowSteps = componentFlowVersion.getFlowSteps();
        for (FlowStep flowStep : flowSteps) {
            Node node = new Node();
            String name = flowStep.getComponentVersion().getComponent().getName();
            String type = flowStep.getComponentVersion().getComponent().getType();
            String imageText = String
                    .format("<img style=\"display: block; margin-left: auto; margin-right: auto\" src=\"data:image/png;base64,%s\"/>",
                            designComponentPalette
                                    .getBase64RepresentationOfImageForComponentType(type));
            node.setText(imageText + "<br><i>" + name + "</i>");
            node.setId(flowStep.getId());
            node.setX(flowStep.getX());
            node.setY(flowStep.getY());
            diagram.addNode(node);

            for (FlowStepLink link : links) {
                if (link.getSourceStepId().equals(node.getId())) {
                    node.getTargetNodeIds().add(link.getTargetStepId());
                }
            }

        }

    }

    protected void openExecution() {
        IAgentManager agentManager = context.getAgentManager();
        Set<Agent> agents = agentManager.getLocalAgents();
        Agent localAgent = null;
        for (Agent agent : agents) {
            if (agent.getHost().equals("localhost")) {
                localAgent = agent;
                break;
            }
        }

        if (localAgent == null) {
            localAgent = new Agent();
            localAgent.setHost("localhost");
            localAgent.setName("local");
            localAgent.setStartMode(AgentStartMode.AUTO.name());
            context.getConfigurationService().save(localAgent);
            agentManager.refresh(localAgent);
        }

        AgentDeployment deployment = localAgent.getAgentDeploymentFor(componentFlowVersion);
        if (deployment != null) {
            agentManager.undeploy(deployment);

        }

        deployment = agentManager.deploy(localAgent.getId(), componentFlowVersion);

        String executionId = agentManager.getAgentRuntime(localAgent).scheduleNow(deployment);
        if (executionId != null) {
            ExecutionLogPanel logPanel = new ExecutionLogPanel(executionId, context);
            tabs.addCloseableTab(executionId, "Run " + componentFlowVersion.getFlow().getName()
                    + " " + componentFlowVersion.getName(), Icons.LOG, logPanel);
        }
    }

    class DiagramChangedListener implements Listener {
        private static final long serialVersionUID = 1L;

        @Override
        public void componentEvent(Event e) {
            IConfigurationService configurationService = context.getConfigurationService();
            if (e instanceof NodeSelectedEvent) {
                NodeSelectedEvent event = (NodeSelectedEvent) e;
                Node node = event.getNode();
                FlowStep flowStep = componentFlowVersion.findFlowStepWithId(node.getId());
                designPropertySheet.valueChange(flowStep.getComponentVersion());
                designNavigator.select(flowStep);

            } else if (e instanceof NodeMovedEvent) {
                NodeMovedEvent event = (NodeMovedEvent) e;
                Node node = event.getNode();
                FlowStep flowStep = componentFlowVersion.findFlowStepWithId(node.getId());
                if (flowStep != null) {
                    flowStep.setX(node.getX());
                    flowStep.setY(node.getY());
                }
                configurationService.save(componentFlowVersion);

            } else if (e instanceof ResourceEvent) {
                ResourceEvent event = (ResourceEvent) e;
                if (!event.isRemoved()) {
                    componentFlowVersion.getFlowStepLinks().add(
                            new FlowStepLink(event.getSourceNodeId(), event.getTargetNodeId()));
                    configurationService.save(componentFlowVersion);
                } else {
                    FlowStepLink link = componentFlowVersion.removeFlowStepLink(
                            event.getSourceNodeId(), event.getTargetNodeId());
                    if (link != null) {
                        configurationService.delete(link);
                        redrawFlow();
                    }

                }
            }
        }
    }

    class DropHandler implements com.vaadin.event.dd.DropHandler {
        private static final long serialVersionUID = 1L;

        @Override
        public void drop(DragAndDropEvent event) {
            WrapperTransferable t = (WrapperTransferable) event.getTransferable();
            WrapperTargetDetails details = (WrapperTargetDetails) event.getTargetDetails();
            DragAndDropWrapper wrapper = (DragAndDropWrapper) t.getSourceComponent();
            Button button = (Button) wrapper.iterator().next();
            Component component = new Component();
            component.setType(button.getCaption());
            component.setShared(false);
            addComponent(details.getMouseEvent().getClientX() - details.getAbsoluteLeft(), details
                    .getMouseEvent().getClientY() - details.getAbsoluteTop(), component);
        }

        @Override
        public AcceptCriterion getAcceptCriterion() {
            return AcceptAll.get();
        }
    }

}