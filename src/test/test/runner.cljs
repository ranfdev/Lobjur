(ns test.runner
  "Test runner for GJS environment"
  (:require [cljs.test :refer-macros [run-tests]]
            [test.api.helpers-test]
            [test.api.hal-test]))

(defn main []
  ;; Enable console output for test results
  (enable-console-print!)
  
  ;; Run all tests
  (println "\n=== Running Tests ===")
  (run-tests 'test.api.helpers-test
             'test.api.hal-test)
  
  ;; Give async tests time to complete before exiting
  ;; In a real scenario, you'd want to track test completion more carefully
  (js/setTimeout
    (fn []
      (println "\n=== Tests Complete ===")
      (let [system (js* "imports.system")]
        ;; Exit successfully - tests will have printed their results
        (.exit system 0)))
    2000))
