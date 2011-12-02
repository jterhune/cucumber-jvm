package cucumber.runtime.model;

import cucumber.runtime.Backend;
import cucumber.runtime.Runtime;
import cucumber.runtime.World;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Row;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.Step;

import java.util.List;

public class CucumberScenario extends CucumberTagStatement {
    private final CucumberBackground cucumberBackground;
    private World world;

    public CucumberScenario(CucumberFeature cucumberFeature, CucumberBackground cucumberBackground, Scenario scenario) {
        super(cucumberFeature, scenario);
        this.cucumberBackground = cucumberBackground;
    }

    public CucumberScenario(CucumberFeature cucumberFeature, CucumberBackground cucumberBackground, Scenario exampleScenario, Row example) {
        super(cucumberFeature, exampleScenario, example);
        this.cucumberBackground = cucumberBackground;
    }

    public void buildWorldAndRunBeforeHooks(List<String> gluePaths, Runtime runtime) {
        world = new World(runtime, tags());
        world.buildBackendWorldsAndRunBeforeHooks(gluePaths);
    }

    @Override
    public void run(Formatter formatter, Reporter reporter, Runtime runtime, List<? extends Backend> backends, List<String> gluePaths) {
        // TODO: Maybe get extraPaths from scenario

        // TODO: split up prepareAndFormat so we can run Background in isolation.
        // Or maybe just try to make Background behave like a regular Scenario?? Printing wise at least.

        List<Step> steps = getSteps();

        try {
            buildWorldAndRunBeforeHooks(gluePaths, runtime);
        } catch (Throwable e) {
            //TODO some of this is specific to grails-cucumber.  After some refactoring, we can probably clean some of this up.

            formatter.scenario((Scenario) tagStatement);

            // Need to use dummy matches and steps, otherwise PrettyFormatter.result() will fail when PrettyFormatterWrapper calls it.
            reporter.match(Match.UNDEFINED);
            formatter.step(steps.get(0));

            Result exceptionResult = new Result(Result.FAILED, 0L, e, new Object());
            reporter.result(exceptionResult);
            return;
        }

        //TODO check for Throwable returned by runBackground()?
        runBackground(formatter, reporter);

        //TODO StepContainer.format() calls formatter.step().  Shouldn't this be done at the same time the step is run??
        format(formatter);
        for (Step step : steps) {
            runStep(step, reporter);
        }
        runAfterHooksAndDisposeWorld();
    }

    public Throwable runBackground(Formatter formatter, Reporter reporter) {
        if (cucumberBackground == null) {
            return null;
        }
        cucumberBackground.format(formatter);
        List<Step> steps = cucumberBackground.getSteps();
        Throwable failure = null;
        for (Step step : steps) {
            Throwable e = runStep(step, reporter);
            if (e != null) {
                failure = e;
            }
        }
        return failure;
    }

    public Throwable runStep(Step step, Reporter reporter) {
        return world.runStep(getUri(), step, reporter, getLocale());
    }

    public void runAfterHooksAndDisposeWorld() {
        world.runAfterHooksAndDisposeBackendWorlds();
    }

}
