(ns transducers)

;; Let's start with a framework of thinking
;; A reduction process (e.g reduce) is like
;; a (possibly infinite) conveyor belt of values
;; as the values pass through the belt
;; there are certain "machines" or "mechanisms"
;; that may want to perform modifications to the values
;; or filter them out of the belt
;; the machines at the factory know nothing about 
;; what is being handled in the belt
;; they only know what they've been told to do with the values
;; it is up to a higher level entity (dev) to define the rules by which the machines
;; will handle the values passing through them

;; So in a way it is like satisfactory/factorio
;; Where we have resources (values) that come through a belt
;; and they pass through a series of transformations
;; e.g they become other resources or get transformed 
;; they get tagged or filtered/moved to another conveyor belt
;; what happens to them prior/after the transformation in the machine
;; is of no concern to the machine itself

;; The "technical" way to see it is a like a higher order reducer
;; it's job is to wrap another reducing function and modify it's process a little bit

(def run-factory transduce)

(comment

  ;; Machine 1 is an announcer of work
  ;; It's job is to simply watch the production line and say
  ;; "hey, this is what we're working with today"
  ;; when the work is done it says "okay, this is what we got done today"
  (defn machine-1 [msg]
    (fn [next-machine] ; a.k.a reducing function
      (fn

        ([] (next-machine)) ; Init
        ([result]
         (println "we're done!" {:final-result result})
         (next-machine result)) ; Completion
        ([result new-input] ; Work
         ;; This machine doesn't do anything, it just says "hey this is passing through"
         (println msg {:result-so-far result
                       :new-input new-input})
         (next-machine result new-input)))))

  (run-factory (machine-1 "input coming through") conj [] [1 2 3])

  ;; This machine has two roles (for illustration)
  ;; it receives a predicate (when to act) and an operation (how to act)
  (defn machine-2 [pred op]
    (fn [next-machine] ; next machine in the conveyor belt
      (fn
        ([] (next-machine)) ; init
        ([result]
         (println "")
         (println "machine-2 is done" {:result result} "\n")
         (next-machine result)) ; Completion
        ([result new-input]
         (let [should-act (pred new-input)
               acted-upon (op new-input)]
           (println "passing through machine 2"
                    {:result-so-far result
                     :input new-input
                     :should-act should-act
                     :acted-upon acted-upon})
           (if should-act
             ; calls the next machine after acting on the value
             (next-machine result acted-upon)
             ; If we got instructions to not act on the value
             ; then we don't change anything, we stop here
             ; someone else will take care of it
             result))))))

  (run-factory (machine-2 #(odd? %) #(* 2 %))
               conj [] [1 2 3 4 5 6])

  ;; And finally, transducers are composable
  ;; So we can simply join them together
  ;; creating what can be thought of as a "pipeline of transformations"

  ;; Machine 3 is the combination of machine-1 and machine-2
  ;; so it will do everything in the pipeline for each item in it
  ;; it will announce the input coming through
  ;; filter the ones that we are not interested in
  ;; and return the ones that we are, after transforming them a little bit
  (def machine-3
    (comp (machine-1 "input coming through")
          (machine-2 #(odd? %) #(* 2 %))))

  (run-factory machine-3 conj [] [1 2 3 4 5 6 7 8 9 10])




  ;; And since transducers are composable
  ;; and the wrapping of a transducer returns another transducer
  ;; we can create another machine that composes the composition of a machine
  ;; hence machines 4 and 5

  ;; a completion machine, it says when we are done and what we did
  (defn machine-4 [msg]
    (fn [next-machine] ; a.k.a reducing function
      (fn

        ([] (next-machine)) ; Init
        ([result]
         (print msg {:final-result result})
         (next-machine result)) ; Completion
        ([result new-input] ; Work, nothing to do
         (next-machine result new-input)))))

  (def machine-5
    (comp machine-3
          (machine-4 "we are finally done, good work boys!")))

  (run-factory machine-5 conj [] [1 2 3 4 5 6 7 8 9 10])

  ;
  )
