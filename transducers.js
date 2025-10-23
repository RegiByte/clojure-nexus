// ============================================
// TRANSDUCER BUILDING BLOCKS
// ============================================

/**
 * Creates a mapping transducer that transforms each value
 * @param {Function} mapFn - The function to transform each value
 * @returns {Function} - A transducer function that applies the mapping
 */
const mapping = (mapFn) => (reducingFunction) => (result, newInput) => {
  return reducingFunction(result, mapFn(newInput));
};

/**
 * Creates a filtering transducer that selectively passes values to the reducing function
 * @param {Function} filterFn - The function that returns true to keep the value
 * @returns {Function} - A transducer function that applies the filtering
 */
const filtering = (filterFn) => (reducingFunction) => (result, newInput) => {
  if (filterFn(newInput)) {
    return reducingFunction(result, newInput);
  }
  return result;
};

/**
 * Creates an observing transducer for debugging the transformation pipeline
 * @param {string} label - Label to identify this observation point
 * @returns {Function} A transducer that logs values without changing them
 */
const observe = (label) => (reducingFunction) => (result, input) => {
  console.log(`[${label}]`, { accumulated: result, current: input });
  return reducingFunction(result, input);
};

// ============================================
// TRANSDUCER COMPOSITION
// ============================================

/**
 * Composes multiple transducers into a single transducer pipeline
 * Transducers are applied from left to right (top to bottom)
 * @param {...Function} transducers - Transducers to compose
 * @returns {Function} A single composed transducer
 */
const compose = (...transducers) =>
  transducers.reduce(
    (composedSoFar, currentTransducer) => (reducingFn) =>
      composedSoFar(currentTransducer(reducingFn))
  );

// ============================================
// EXAMPLE USAGE
// ============================================

// Build a transformation pipeline
const transformationPipeline = compose(
  mapping((x) => x * x), // Square each number
  filtering((x) => x < 80) // Keep only values less than 80
);

// Create the final reducing function by passing in a base reducer
const arrayConcat = (acc, value) => acc.concat(value);
const arraySum = (a, b) => a + b;
const transducedReducer = transformationPipeline(arrayConcat);

// Apply the transduced reducer to our data
const inputData = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const finalResult = inputData.reduce(transducedReducer, []);

console.log("\n=== FINAL RESULT ===");
console.log(finalResult);
