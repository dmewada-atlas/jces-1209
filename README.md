## Compare Jira performance

Benchmark two Jiras against each other. Both [Jira Cloud] and [Jira Data Center] are supported.

1. Prepare [AWS credentials].
2. Point to instances via property files in `cohort-secrets`, akin to the [example properties].
3. Run `./gradlew comparePerformance`.

    Read various reports logged in the console. The most useful ones are links to files,
    like `distribution-comparision.html` or `summary-per-cohort.html`.

[Jira Cloud]: docs/SET-UP-CLOUD.md
[Jira Data Center]: docs/PROVISION-DC.md
[AWS credentials]: docs/AWS-CREDENTIALS.md
[example properties]: cohort-secrets/example.properties

### AWS - Provision nodes to a vpc and subnet
You specify both vpc and subnet of the nodes provisioned for JPT when using SlowAndMeaningful benchmark quality by 
providing values for following as env variables:
* subnetId
* vpcId  

### Prior art
Originally forked from [quick-303](https://github.com/atlassian/quick-303).
