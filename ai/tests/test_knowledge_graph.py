import unittest

from app.main import build_knowledge_graph_response
from app.repository import KnowledgeGraphEdge, KnowledgeGraphNode


class KnowledgeGraphResponseTest(unittest.TestCase):
    def test_groups_connected_sources_without_exposing_chunks(self):
        response = build_knowledge_graph_response(
            [
                KnowledgeGraphNode("knowledge-k1", "projectKnowledge", "Access policy", None),
                KnowledgeGraphNode("meeting-m1", "meeting", "Architecture review", "meeting-1"),
                KnowledgeGraphNode("meeting-m2", "meeting", "Budget review", "meeting-2"),
            ],
            [KnowledgeGraphEdge("knowledge-k1", "meeting-m1", 0.84)],
        )

        self.assertEqual(2, len(response.clusters))
        self.assertEqual(2, response.clusters[0].sourceCount)
        self.assertEqual({"knowledge-k1", "meeting-m1"}, {node.id for node in response.clusters[0].nodes})
        self.assertEqual("meeting-1", next(node.sourceMeetingId for node in response.clusters[0].nodes if node.id == "meeting-m1"))
        self.assertEqual("COMPLETED", response.clusters[0].nodes[0].embeddingStatus)
        self.assertEqual("knowledge-k1", response.edges[0].from_)

    def test_empty_snapshot_returns_empty_graph(self):
        response = build_knowledge_graph_response([], [])

        self.assertEqual([], response.clusters)
        self.assertEqual([], response.edges)


if __name__ == "__main__":
    unittest.main()
