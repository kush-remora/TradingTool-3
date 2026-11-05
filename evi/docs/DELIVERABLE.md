# EY Visual Book Generator

## The problem

Marketing teams create large, content-heavy documents, sometimes up to 500 pages.

The content is already there. The team has written it, chosen the headings, organised the lists, and prepared the PDF.

But turning that much content into a well-designed layout still takes a designer a long time:

- the first typesetting pass is manual
- the layout goes through many iterations
- the whole process takes significant time

The designer is not the problem. The work is simply repetitive and expensive. The designer still needs to review the result and improve it, but AI can create a useful first version of the layout.

This prototype asks AI to create that first typesetting pass. It gives the designer a starting point instead of a blank page.

The designer remains in the loop. They can review the result, choose a direction, and take it to the final version.

You give it:

- a source PDF
- an image
- a choice about how much AI may change

It gives you a first layout direction for the page.

## The three choices

### Preserve

Keep the original page exactly as it is.

The image is added as a separate page. One option puts it before the source page. The other puts it after.

### Restructure

Keep the original words.

LLM may improve the order, headings, lists, and page layout.

### Transform

Give LLM full creative control.

It may rewrite, shorten, summarize, and redesign the document. It returns two genuinely different ways to tell the same story.

## How the prototype works

1. Upload a document.
2. Add a visual.
3. Choose Preserve, Restructure, or Transform.
4. The LLM returns a structured result.
5. Review the visual options.
6. Choose one and move to the next page.

The important part is that the user decides how much freedom the LLM gets.

## Our approach

We kept the first version simple:

- React provides the web interface.
- The document is processed page by page in this prototype. The target workflow is large documents, including documents of up to 500 pages.
- The three LLM instructions live in separate prompt files.
- The LLM returns structured JSON instead of an unstructured answer.
- The UI turns that result into a visual preview.

The prompts act like rules:

- Preserve protects the source page.
- Restructure protects the words.
- Transform protects the facts.

## Assumptions

- The source document is a PDF.
- The user can provide the visual that belongs with the page.
- This first prototype demonstrates page-level processing rather than processing a complete 500-page document in one run.
- One uploaded image is enough for the first version.
- The LLM can follow the selected authority rule.
- The final PDF renderer will use the selected result later.

## What this prototype proves

It proves the main interaction:

> Upload a source, choose the AI's freedom, see the result, and choose a direction.

The final export pipeline and deeper automatic checks are the next steps.
