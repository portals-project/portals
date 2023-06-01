
// Invoke ApplicationBuilder directly
let applicationBuilder = PortalsJS.ApplicationBuilder('GeneratorBuilder');

// Assuming that generators is a function that returns an instance of GeneratorBuilderJS
let generatorBuilder = applicationBuilder.generators;

// Assuming we have some JS Array and Iterators
let array = [1, 2, 3, 4];
let arrayarray = [[1, 2], [3, 4]];
let keys = ["key1", "key2"];
let it = array[Symbol.iterator]();
let itit = arrayarray.map(arr => arr[Symbol.iterator]());
let start = 0;
let end = 10;
let step = 2;

// Test fromIterator methods
try {
    let fromIteratorResult = generatorBuilder.fromIterator(it);
    console.log('fromIterator method executed successfully. Result:', fromIteratorResult);
} catch (error) {
    console.error('Error when executing fromIterator method:', error);
}

try {
    let fromIteratorResult = generatorBuilder.fromIterator(it, keys[Symbol.iterator]());
    console.log('fromIterator method executed successfully. Result:', fromIteratorResult);
} catch (error) {
    console.error('Error when executing fromIterator method:', error);
}

// Test fromIteratorOfIterators methods
try {
    let fromIteratorOfIteratorsResult = generatorBuilder.fromIteratorOfIterators(itit);
    console.log('fromIteratorOfIterators method executed successfully. Result:', fromIteratorOfIteratorsResult);
} catch (error) {
    console.error('Error when executing fromIteratorOfIterators method:', error);
}

try {
    let fromIteratorOfIteratorsResult = generatorBuilder.fromIteratorOfIterators(itit, itit);
    console.log('fromIteratorOfIterators method executed successfully. Result:', fromIteratorOfIteratorsResult);
} catch (error) {
    console.error('Error when executing fromIteratorOfIterators method:', error);
}

// Test fromArray methods
try {
    let fromArrayResult = generatorBuilder.fromArray(array);
    console.log('fromArray method executed successfully. Result:', fromArrayResult);
} catch (error) {
    console.error('Error when executing fromArray method:', error);
}

try {
    let fromArrayResult = generatorBuilder.fromArray(array, keys);
    console.log('fromArray method executed successfully. Result:', fromArrayResult);
} catch (error) {
    console.error('Error when executing fromArray method:', error);
}

// Test fromArrayOfArrays methods
try {
    let fromArrayOfArraysResult = generatorBuilder.fromArrayOfArrays(arrayarray);
    console.log('fromArrayOfArrays method executed successfully. Result:', fromArrayOfArraysResult);
} catch (error) {
    console.error('Error when executing fromArrayOfArrays method:', error);
}

try {
    let fromArrayOfArraysResult = generatorBuilder.fromArrayOfArrays(arrayarray, arrayarray);
    console.log('fromArrayOfArrays method executed successfully. Result:', fromArrayOfArraysResult);
} catch (error) {
    console.error('Error when executing fromArrayOfArrays method:', error);
}

// Test fromRange method
try {
    let fromRangeResult = generatorBuilder.fromRange(start, end, step);
    console.log('fromRange method executed successfully. Result:', fromRangeResult);
} catch (error) {
    console.error('Error when executing fromRange method:', error);
}
