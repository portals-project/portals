// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('test_name');

// Assuming that flows is a function that returns an instance of FlowBuilderJS
let flowBuilder = applicationBuilder.flows;

// Test freeze method
try {
    let freezeResult = flowBuilder.freeze();
    console.log('freeze method executed successfully. Result:', freezeResult);
} catch (error) {
    console.error('Error when executing freeze method:', error);
}

// Test sink method
try {
    let sinkResult = flowBuilder.sink();
    console.log('sink method executed successfully. Result:', sinkResult);
} catch (error) {
    console.error('Error when executing sink method:', error);
}

// Test union method
try {
    // You should replace this example array with actual FlowBuilderJS instances
    let others = [flowBuilder, flowBuilder];
    let unionResult = flowBuilder.union(others);
    console.log('union method executed successfully. Result:', unionResult);
} catch (error) {
    console.error('Error when executing union method:', error);
}

try {
    let mapFunction = function(ctx, input) { /* TODO: Write your implementation here */ };
    let mapResult = flowBuilder.map(mapFunction);
    console.log('map method executed successfully. Result:', mapResult);
} catch (error) {
    console.error('Error when executing map method:', error);
}

// Test key method
try {
    let keyFunction = function(input) { /* TODO: Write your implementation here */ };
    let keyResult = flowBuilder.key(keyFunction);
    console.log('key method executed successfully. Result:', keyResult);
} catch (error) {
    console.error('Error when executing key method:', error);
}

// Test task method
try {
    let genericTask = { /* TODO: Create a GenericTask instance */ };
    let taskResult = flowBuilder.task(genericTask);
    console.log('task method executed successfully. Result:', taskResult);
} catch (error) {
    console.error('Error when executing task method:', error);
}

// Test processor method
try {
    let processorFunction = function(ctx, input) { /* TODO: Write your implementation here */ };
    let processorResult = flowBuilder.processor(processorFunction);
    console.log('processor method executed successfully. Result:', processorResult);
} catch (error) {
    console.error('Error when executing processor method:', error);
}

// Test flatMap method
try {
    let flatMapFunction = function(ctx, input) { /* TODO: Write your implementation here */ };
    let flatMapResult = flowBuilder.flatMap(flatMapFunction);
    console.log('flatMap method executed successfully. Result:', flatMapResult);
} catch (error) {
    console.error('Error when executing flatMap method:', error);
}

// Test filter method
try {
    let filterFunction = function(input) { /* TODO: Write your implementation here */ };
    let filterResult = flowBuilder.filter(filterFunction);
    console.log('filter method executed successfully. Result:', filterResult);
} catch (error) {
    console.error('Error when executing filter method:', error);
}

// Test vsm method
try {
    let vsmTask = { /* TODO: Create a VSMTask instance */ };
    let vsmResult = flowBuilder.vsm(vsmTask);
    console.log('vsm method executed successfully. Result:', vsmResult);
} catch (error) {
    console.error('Error when executing vsm method:', error);
}

// Test init method
try {
    let initFactory = function(ctx) { /* TODO: Write your implementation here */ };
    let initResult = flowBuilder.init(initFactory);
    console.log('init method executed successfully. Result:', initResult);
} catch (error) {
    console.error('Error when executing init method:', error);
}

// Test identity method
try {
    let identityResult = flowBuilder.identity();
    console.log('identity method executed successfully. Result:', identityResult);
} catch (error) {
    console.error('Error when executing identity method:', error);
}


// Test logger method
try {
    let prefix = "log-prefix";
    let loggerResult = flowBuilder.logger(prefix);
    console.log('logger method executed successfully. Result:', loggerResult);
} catch (error) {
    console.error('Error when executing logger method:', error);
}

// Test checkExpectedType method
try {
    let checkExpectedTypeResult = flowBuilder.checkExpectedType();
    console.log('checkExpectedType method executed successfully. Result:', checkExpectedTypeResult);
} catch (error) {
    console.error('Error when executing checkExpectedType method:', error);
}

// Test withName method
try {
    let name = "test-name";
    let withNameResult = flowBuilder.withName(name);
    console.log('withName method executed successfully. Result:', withNameResult);
} catch (error) {
    console.error('Error when executing withName method:', error);
}

// Test withOnNext method
try {
    let onNextFunction = function(ctx, input) { /* TODO: Write your implementation here */ };
    let withOnNextResult = flowBuilder.withOnNext(onNextFunction);
    console.log('withOnNext method executed successfully. Result:', withOnNextResult);
} catch (error) {
    console.error('Error when executing withOnNext method:', error);
}

// Test withOnError method
try {
    let onErrorFunction = function(ctx, error) { /* TODO: Write your implementation here */ };
    let withOnErrorResult = flowBuilder.withOnError(onErrorFunction);
    console.log('withOnError method executed successfully. Result:', withOnErrorResult);
} catch (error) {
    console.error('Error when executing withOnError method:', error);
}

// Test withOnComplete method
try {
    let onCompleteFunction = function(ctx) { /* TODO: Write your implementation here */ };
    let withOnCompleteResult = flowBuilder.withOnComplete(onCompleteFunction);
    console.log('withOnComplete method executed successfully. Result:', withOnCompleteResult);
} catch (error) {
    console.error('Error when executing withOnComplete method:', error);
}


// Test withOnAtomComplete method
try {
    let onAtomCompleteFunction = function(ctx) { /* TODO: Write your implementation here */ };
    let withOnAtomCompleteResult = flowBuilder.withOnAtomComplete(onAtomCompleteFunction);
    console.log('withOnAtomComplete method executed successfully. Result:', withOnAtomCompleteResult);
} catch (error) {
    console.error('Error when executing withOnAtomComplete method:', error);
}

// Test withWrapper method
try {
    let wrapperFunction = function(ctx, input) { /* TODO: Write your implementation here */ };
    let withWrapperResult = flowBuilder.withWrapper(wrapperFunction);
    console.log('withWrapper method executed successfully. Result:', withWrapperResult);
} catch (error) {
    console.error('Error when executing withWrapper method:', error);
}

// Test withStep method
try {
    let task = {};  // TODO: Replace with an actual GenericTask instance
    let withStepResult = flowBuilder.withStep(task);
    console.log('withStep method executed successfully. Result:', withStepResult);
} catch (error) {
    console.error('Error when executing withStep method:', error);
}

// Test withLoop method
try {
    let count = 5;
    let task = {};  // TODO: Replace with an actual GenericTask instance
    let withLoopResult = flowBuilder.withLoop(count)(task);
    console.log('withLoop method executed successfully. Result:', withLoopResult);
} catch (error) {
    console.error('Error when executing withLoop method:', error);
}

// Test withAndThen method
try {
    let task = {};  // TODO: Replace with an actual GenericTask instance
    let withAndThenResult = flowBuilder.withAndThen(task);
    console.log('withAndThen method executed successfully. Result:', withAndThenResult);
} catch (error) {
    console.error('Error when executing withAndThen method:', error);
}

// Test allWithOnAtomComplete method
try {
    let onAtomCompleteFunction = function(ctx) { /* TODO: Write your implementation here */ };
    let allWithOnAtomCompleteResult = flowBuilder.allWithOnAtomComplete(onAtomCompleteFunction);
    console.log('allWithOnAtomComplete method executed successfully. Result:', allWithOnAtomCompleteResult);
} catch (error) {
    console.error('Error when executing allWithOnAtomComplete method:', error);
}

// Test allWithWrapper method
try {
    let wrapperFunction = function(ctx, input) { /* TODO: Write your implementation here */ };
    let allWithWrapperResult = flowBuilder.allWithWrapper(wrapperFunction);
    console.log('allWithWrapper method executed successfully. Result:', allWithWrapperResult);
} catch (error) {
    console.error('Error when executing allWithWrapper method:', error);
}

// Test asker method
try {
    let portals = {};  // TODO: Replace with an actual AtomicPortalRefKind instance
    let functionToExecute = function(ctx, input) { /* TODO: Write your implementation here */ };
    let askerResult = flowBuilder.asker(portals)(functionToExecute);
    console.log('asker method executed successfully. Result:', askerResult);
} catch (error) {
    console.error('Error when executing asker method:', error);
}

// Test replier method
try {
    let portals = {};  // TODO: Replace with an actual AtomicPortalRefKind instance
    let function1 = function(ctx, input) { /* TODO: Write your implementation here */ };
    let function2 = function(ctx, request) { /* TODO: Write your implementation here */ };
    let replierResult = flowBuilder.replier(portals)(function1)(function2);
    console.log('replier method executed successfully. Result:', replierResult);
} catch (error) {
    console.error('Error when executing replier method:', error);
}