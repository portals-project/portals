// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('TaskBuilder');

// Assuming that tasks is a function that returns an instance of TaskBuilderJS
let taskBuilder = applicationBuilder.tasks;

// Test processor method
try {
    let processorOnNext = function(ctx, input) {
        // This is a simple example that logs the input
        // Replace this with your own logic
        console.log(`Processor received input: ${input}`);
    };
    let processorResult = taskBuilder.processor(processorOnNext);
    console.log('processor method executed successfully. Result:', processorResult);
} catch (error) {
    console.error('Error when executing processor method:', error);
}

// Test map method
try {
    let mapFunc = function(ctx, input) {
        // This is a simple example that returns the input squared
        // Replace this with your own logic
        return input * input;
    };
    let mapResult = taskBuilder.map(mapFunc);
    console.log('map method executed successfully. Result:', mapResult);
} catch (error) {
    console.error('Error when executing map method:', error);
}

// Test flatMap method
try {
    let flatMapFunc = function(ctx, input) {
        // This is a simple example that returns an array with the input and its square
        // Replace this with your own logic
        return [input, input * input];
    };
    let flatMapResult = taskBuilder.flatMap(flatMapFunc);
    console.log('flatMap method executed successfully. Result:', flatMapResult);
} catch (error) {
    console.error('Error when executing flatMap method:', error);
}


// Test identity method
try {
    let identityResult = taskBuilder.identity();
    console.log('identity method executed successfully. Result:', identityResult);
} catch (error) {
    console.error('Error when executing identity method:', error);
}

// Test filter method
try {
    let filterFunc = function(input) { return input > 5; }; // Filter out numbers less than 5
    let filterResult = taskBuilder.filter(filterFunc);
    console.log('filter method executed successfully. Result:', filterResult);
} catch (error) {
    console.error('Error when executing filter method:', error);
}

// Test key method
try {
    let keyFunc = function(input) { return input * 5; }; // Multiply each input by 5
    let keyResult = taskBuilder.key(keyFunc);
    console.log('key method executed successfully. Result:', keyResult);
} catch (error) {
    console.error('Error when executing key method:', error);
}
