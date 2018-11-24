(ns gorilla-repl.version-test
  (:require [clojure.test :refer :all]
            [gorilla-repl.version :refer :all]))

(deftest version-check-test
  (testing "Does comparing versions work?"
    (is (.startsWith
          (with-out-str (version-check "0.5.0" "0.4.0"))
                        "A newer version"))))
