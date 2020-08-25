package jces1209

import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.regions.Regions.*
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Vpc
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.DependentResources
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.ProvisionedVirtualUsers
import com.atlassian.performance.tools.infrastructure.api.virtualusers.DirectResultsTransport
import com.atlassian.performance.tools.io.api.dereference
import com.atlassian.performance.tools.io.api.ensureDirectory
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

class AwsVus(
    duration: Duration,
    private val region: Regions,
    private val vpcId: String?,
    private val subnetId: String?
) : VirtualUsersSource {

    private val lifespan = Duration.ofMinutes(10) + duration

    override fun obtainVus(
        resultsTarget: Path,
        workspace: Path
    ): ProvisionedVirtualUsers<*> {
        val aws = prepareAws()
        val nonce = UUID.randomUUID().toString()
        val sshKey = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = workspace,
            prefix = nonce,
            lifespan = lifespan
        ).provision()
        val investment = Investment(
            useCase = "Compare two Jiras the Falcon way",
            lifespan = lifespan
        )
        val network = if (vpcId != null && subnetId != null) {
            Network(
                Vpc().withVpcId(vpcId),
                Subnet().withSubnetId(subnetId)
            )
        } else {
            NetworkFormula(
                investment = investment,
                aws = aws
            ).provision()
        }
        val provisioned = MulticastVirtualUsersFormula.Builder(
                nodes = 6,
                shadowJar = dereference("jpt.virtual-users.shadow-jar")
            )
            .browser(Chromium77())
            .network(network)
            .build()
            .provision(
                investment = investment,
                shadowJarTransport = aws.virtualUsersStorage(nonce),
                resultsTransport = workAroundDirectResultTransportRaceCondition(resultsTarget),
                roleProfile = aws.shortTermStorageAccess(),
                key = CompletableFuture.completedFuture(sshKey),
                aws = aws
            )
        return ProvisionedVirtualUsers(
            virtualUsers = provisioned.virtualUsers,
            resource = DependentResources(
                user = provisioned.resource,
                dependency = sshKey.remote
            )
        )
    }

    private fun workAroundDirectResultTransportRaceCondition(
        resultsTarget: Path
    ): DirectResultsTransport {
        resultsTarget.resolve("virtual-users").ensureDirectory()
        return DirectResultsTransport(resultsTarget)
    }

    private fun prepareAws() = Aws.Builder(region)
        .credentialsProvider(
            AWSCredentialsProviderChain(
                STSAssumeRoleSessionCredentialsProvider.Builder(
                    "arn:aws:iam::695067801333:role/server-gdn-bamboo",
                    UUID.randomUUID().toString()
                ).build(),
                ProfileCredentialsProvider("jpt-dev"),
                EC2ContainerCredentialsProviderWrapper(),
                DefaultAWSCredentialsProviderChain()
            )
        )
        .regionsWithHousekeeping(
            // https://server-gdn-bamboo.internal.atlassian.com/browse/JIRA-JPTH
            listOf(US_EAST_1, US_WEST_1, EU_CENTRAL_1, EU_WEST_1, EU_WEST_2)
        )
        .batchingCloudformationRefreshPeriod(Duration.ofSeconds(20))
        .build()
}
